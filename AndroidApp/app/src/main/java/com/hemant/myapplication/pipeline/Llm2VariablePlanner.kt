package com.hemant.myapplication.pipeline

import com.hemant.myapplication.domain.DomainAdapter
import com.hemant.myapplication.tools.DefaultToolRegistry
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

class Llm2VariablePlanner(private val apiKey: String) {
    private val messages = ArrayList<JSONObject>()
    private val variableNamePattern = Regex("[A-Za-z][A-Za-z0-9_]*")

    @Throws(Exception::class)
    suspend fun planInitial(
        userQuery: String,
        routerOutput: RouterOutput,
        adapter: DomainAdapter,
        defaultTools: DefaultToolRegistry,
    ): VariablePlan {
        // Dynamically discover parameter and response schemas for all domain tools
        val toolsList = defaultTools.getTools() + adapter.getTools()
        val toolsSchemaBuilder = StringBuilder()
        for (tool in toolsList) {
            val paramSchema = if (defaultTools.owns(tool.path)) {
                defaultTools.readTool(tool.path)
            } else {
                adapter.readTool(tool.path)
            }
            val returnSchema = if (defaultTools.owns(tool.path)) {
                defaultTools.readToolResponseSchema(tool.path)
            } else {
                adapter.readToolResponseSchema(tool.path)
            }
            toolsSchemaBuilder.append("- Tool Path: \"${tool.path}\"\n")
            toolsSchemaBuilder.append("  Name: \"${tool.name}\"\n")
            toolsSchemaBuilder.append("  Description: \"${tool.description}\"\n")
            toolsSchemaBuilder.append("  Parameter Schema: ${paramSchema.toString(2)}\n")
            toolsSchemaBuilder.append("  Response/Return Schema: ${returnSchema.toString(2)}\n\n")
        }

        val systemPrompt = """
            You are the Variable Planner (LLM-2) for a plugin-based dynamic widget platform.
            Your task is to analyze the user's query and define a structured plan containing variables and static assets required to render the widget.
            
            You should output a declarative dependency graph of variables. Variables can be either "static" (pre-defined values) or "tool" (executed via an MCP tool call).
            If a variable requires outputs from another variable (like coordinates resolved from a geocoder), refer to that variable's properties using template syntax, e.g. "{{location.latitude}}".

            Every variable MUST include:
            - `variable_name`: a stable identifier used to bind the runtime value under `/model/<variable_name>`.
            - `variable_type`: string, number, boolean, object, or array.
            - `description`: INTERNAL semantic metadata for the layout generator. Explain what the value represents, its hierarchy, and the best way to visualize it. This description is never user-facing.
            - `presentation_hints`: optional internal hints such as "summary", "comparison", "ranked_list", "timeline", "checklist", "metrics", "cards", or "chips".
            - `exposure`: `visible` when the value may be included in the runtime model, or `internal` when it is support data used only by another tool. Internal values are never displayed or sent to the layout generator.

            Domain-specific guidance:
            ${adapter.getSystemPromptGuidance()}
            
            DO NOT attempt to call geocoding or forecast tools yourself during planning. Instead, define them as "tool" variables in the plan so the client can resolve them.
            Default tools are available in every domain. Use `/tool/default/current-location` only when a location-aware request has no user-provided location; mark that variable `exposure: "internal"`.

            If the selected domain is `generic`, generate the requested information as one or more non-empty STATIC variables using your general knowledge. Use `source.type = "static"` and place user-facing data only in `source.value`. Do not create tool variables for the generic domain. Never present static knowledge as a current, live, or verified fact.
            
            Here are the dynamic tools available for the selected domain (${routerOutput.domain}):
            ${toolsSchemaBuilder.toString()}
            
            Format of final plan response:
            {
              "status": "finish",
              "variables": [
                {
                  "variable_name": "location",
                  "variable_type": "object",
                  "description": "A resolved place used as input for the forecast. It is internal support data and usually does not need its own visual component.",
                  "presentation_hints": ["hidden_support_data"],
                  "exposure": "internal",
                  "source": {
                    "type": "tool",
                    "tool_path": "/tool/weather/geocode",
                    "parameters": {
                      "location": "<a location explicitly supplied by the user>"
                    }
                  }
                },
                {
                  "variable_name": "weatherForecast",
                  "variable_type": "object",
                  "description": "Current conditions and forecast collections for a location. Best represented by a prominent temperature summary followed by forecast chips or a compact list.",
                  "presentation_hints": ["summary", "chips"],
                  "exposure": "visible",
                  "source": {
                    "type": "tool",
                    "tool_path": "/tool/weather/forecast",
                    "parameters": {
                      "latitude": "{{location.latitude}}",
                      "longitude": "{{location.longitude}}",
                      "name": "{{location.name}}",
                      "country": "{{location.country}}"
                    }
                  }
                }
              ],
              "assets": ["/asset/weather/icons"]
            }

            Example generic static variable:
            {
              "variable_name": "comparison",
              "variable_type": "array",
              "description": "A comparison of several concepts with a name, strengths, and limitations. Best represented as a compact list or cards.",
              "presentation_hints": ["comparison", "cards"],
              "exposure": "visible",
              "source": {
                "type": "static",
                "value": [
                  { "name": "Item one", "strength": "Example strength", "limitation": "Example limitation" }
                ]
              }
            }
            
            Make sure to return ONLY a valid JSON object matching the schema above.
        """.trimIndent()

        messages.clear()
        messages.add(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.add(JSONObject().put("role", "user").put("content", "Analyze this query and create a variable plan: \"$userQuery\""))

        val initialPlan = executeLlmRequest()
        return try {
            validatePlan(initialPlan, adapter, defaultTools)
            initialPlan
        } catch (e: Exception) {
            messages.add(JSONObject().put("role", "user").put("content", "The previous plan was invalid: ${e.message}. Return a corrected plan with the required variable descriptions and valid static/tool sources."))
            val correctedPlan = executeLlmRequest()
            validatePlan(correctedPlan, adapter, defaultTools)
            correctedPlan
        }
    }

    @Throws(Exception::class)
    suspend fun planCorrection(
        errorMsg: String,
        adapter: DomainAdapter,
        defaultTools: DefaultToolRegistry,
    ): VariablePlan {
        messages.add(JSONObject().put("role", "user").put("content", "The variable plan failed to resolve: $errorMsg. Please identify what went wrong, correct the variables or tool parameter bindings, and output a corrected JSON plan matching the schema."))
        return executeLlmRequest().also { validatePlan(it, adapter, defaultTools) }
    }

    private suspend fun executeLlmRequest(): VariablePlan {
        val requestBody = JSONObject()
            .put("model", "gpt-4o")
            .put("messages", JSONArray(messages))
            .put("temperature", 0.1)
            .put("response_format", JSONObject().put("type", "json_object"))

        val payloadStr = requestBody.toString()
        android.util.Log.d("llm_dbg", "LLM-2 Variable Planner Request:\n$payloadStr")
        val responseStr = HttpUtil.post(
            urlStr = "https://api.openai.com/v1/chat/completions",
            jsonPayload = payloadStr,
            apiKey = apiKey,
            connectTimeoutMs = 30000,
            readTimeoutMs = 300000
        )
        android.util.Log.d("llm_dbg", "LLM-2 Variable Planner Response:\n$responseStr")

        val responseJson = JSONObject(responseStr)
        val choice = responseJson.getJSONArray("choices").getJSONObject(0)
        val assistantMessage = choice.getJSONObject("message")
        
        // Save the assistant message into conversation history
        messages.add(assistantMessage)

        var content = assistantMessage.optString("content", "{}").trim()
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1)
        }
        return parsePlan(JSONObject(content.trim()))
    }

    private fun parsePlan(json: JSONObject): VariablePlan {
        val variables = json.optJSONArray("variables") ?: JSONArray()
        val definitions = List(variables.length()) { index ->
            VariableDefinition.fromPlanVariable(variables.getJSONObject(index))
        }
        return VariablePlan(
            status = json.optString("status", "finish"),
            variables = variables,
            assets = json.optJSONArray("assets") ?: JSONArray(),
            definitions = definitions,
        )
    }

    private fun validatePlan(
        plan: VariablePlan,
        adapter: DomainAdapter,
        defaultTools: DefaultToolRegistry,
    ) {
        val names = HashSet<String>()
        val allowedToolPaths = (defaultTools.getTools() + adapter.getTools()).mapTo(HashSet()) { it.path }
        for (index in 0 until plan.variables.length()) {
            val variable = plan.variables.optJSONObject(index)
                ?: throw Exception("Variable at index $index must be an object")
            val name = variable.optString("variable_name")
            if (!variableNamePattern.matches(name) || !names.add(name)) {
                throw Exception("Each variable requires a unique identifier using letters, numbers, and underscores")
            }
            if (variable.optString("description").isBlank()) {
                throw Exception("Variable '$name' is missing its internal description")
            }
            val exposure = variable.optString("exposure", VariableDefinition.EXPOSURE_VISIBLE).lowercase()
            if (exposure !in setOf(VariableDefinition.EXPOSURE_VISIBLE, VariableDefinition.EXPOSURE_INTERNAL)) {
                throw Exception("Variable '$name' has unsupported exposure '$exposure'")
            }
            val source = variable.optJSONObject("source")
                ?: throw Exception("Variable '$name' is missing source")
            val sourceType = source.optString("type")
            if (sourceType !in setOf("static", "tool")) {
                throw Exception("Variable '$name' has unsupported source type '$sourceType'")
            }
            if (sourceType == "static" && (!source.has("value") || source.isNull("value"))) {
                throw Exception("Static variable '$name' is missing source.value")
            }
            if (sourceType == "tool") {
                val toolPath = source.optString("tool_path")
                if (toolPath !in allowedToolPaths) {
                    throw Exception("Variable '$name' references unavailable tool '$toolPath'")
                }
                if (defaultTools.owns(toolPath) && exposure != VariableDefinition.EXPOSURE_INTERNAL) {
                    throw Exception("Default device tool '$toolPath' must use exposure 'internal'")
                }
            }
        }

        if (adapter.domainName() != "generic") return
        if (plan.variables.length() == 0) {
            throw Exception("A generic widget requires at least one static variable")
        }

        for (index in 0 until plan.variables.length()) {
            val variable = plan.variables.getJSONObject(index)
            val name = variable.getString("variable_name")
            val source = variable.optJSONObject("source")
                ?: throw Exception("Static variable '$name' is missing source")
            if (source.optString("type") != "static") {
                throw Exception("Generic variable '$name' must use a static source")
            }
            if (!source.has("value") || source.isNull("value")) {
                throw Exception("Static variable '$name' is missing source.value")
            }
        }
    }
}
