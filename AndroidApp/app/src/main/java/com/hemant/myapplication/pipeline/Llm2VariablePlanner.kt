package com.hemant.myapplication.pipeline

import com.hemant.myapplication.domain.DomainAdapter
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

class Llm2VariablePlanner(private val apiKey: String) {
    private val messages = ArrayList<JSONObject>()

    @Throws(Exception::class)
    suspend fun planInitial(userQuery: String, routerOutput: RouterOutput, adapter: DomainAdapter): VariablePlan {
        if (routerOutput.domain == "generic") {
            val mockPlan = JSONObject()
                .put("status", "finish")
                .put("variables", JSONArray().put(
                    JSONObject()
                        .put("variable_name", "genericInfo")
                        .put("variable_type", "object")
                        .put("source", JSONObject().put("type", "static").put("value", JSONObject()))
                ))
                .put("assets", JSONArray())
            return parsePlan(mockPlan)
        }

        // Dynamically discover parameter and response schemas for all domain tools
        val toolsList = adapter.getTools()
        val toolsSchemaBuilder = StringBuilder()
        for (tool in toolsList) {
            val paramSchema = adapter.readTool(tool.path)
            val returnSchema = adapter.readToolResponseSchema(tool.path)
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
            
            DO NOT attempt to call geocoding or forecast tools yourself during planning. Instead, define them as "tool" variables in the plan so the client can resolve them.
            
            Here are the dynamic tools available for the selected domain (${routerOutput.domain}):
            ${toolsSchemaBuilder.toString()}
            
            Format of final plan response:
            {
              "status": "finish",
              "variables": [
                {
                  "variable_name": "location",
                  "variable_type": "object",
                  "source": {
                    "type": "tool",
                    "tool_path": "/tool/weather/geocode",
                    "parameters": {
                      "location": "Boston"
                    }
                  }
                },
                {
                  "variable_name": "weatherForecast",
                  "variable_type": "object",
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
            
            Make sure to return ONLY a valid JSON object matching the schema above.
        """.trimIndent()

        messages.clear()
        messages.add(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.add(JSONObject().put("role", "user").put("content", "Analyze this query and create a variable plan: \"$userQuery\""))

        return executeLlmRequest()
    }

    @Throws(Exception::class)
    suspend fun planCorrection(errorMsg: String, adapter: DomainAdapter): VariablePlan {
        messages.add(JSONObject().put("role", "user").put("content", "The variable plan failed to resolve: $errorMsg. Please identify what went wrong, correct the variables or tool parameter bindings, and output a corrected JSON plan matching the schema."))
        return executeLlmRequest()
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
        return VariablePlan(
            status = json.optString("status", "finish"),
            variables = json.optJSONArray("variables") ?: JSONArray(),
            assets = json.optJSONArray("assets") ?: JSONArray()
        )
    }
}
