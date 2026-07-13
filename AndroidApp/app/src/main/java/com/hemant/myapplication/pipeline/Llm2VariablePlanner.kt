package com.hemant.myapplication.pipeline

import com.hemant.myapplication.domain.DomainAdapter
import com.hemant.myapplication.domain.DomainRegistry
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

class Llm2VariablePlanner(private val apiKey: String) {
    @Throws(Exception::class)
    suspend fun plan(userQuery: String, routerOutput: RouterOutput, adapter: DomainAdapter): VariablePlan {
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

        // Initialize default tools JSONDefinitions
        val toolDefinitions = JSONArray()
        toolDefinitions.put(JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", "list_skills")
                .put("description", "List available skills supported by a domain.")
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("domain", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("domain"))
                )
            )
        )
        toolDefinitions.put(JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", "list_tools")
                .put("description", "List available tools supported by a domain.")
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("domain", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("domain"))
                )
            )
        )
        toolDefinitions.put(JSONObject()
            .put("type", "function")
            .put("function", JSONObject()
                .put("name", "read_tool")
                .put("description", "Read parameter schema details of a specific tool.")
                .put("parameters", JSONObject()
                    .put("type", "object")
                    .put("properties", JSONObject().put("path", JSONObject().put("type", "string")))
                    .put("required", JSONArray().put("path"))
                )
            )
        )

        // Inject domain specific tools dynamically (mapping /tool/weather/geocode -> tool__weather__geocode)
        adapter.getTools().forEach { tool ->
            val functionName = tool.path.substring(1).replace("/", "__")
            val schema = adapter.readTool(tool.path)
            toolDefinitions.put(JSONObject()
                .put("type", "function")
                .put("function", JSONObject()
                    .put("name", functionName)
                    .put("description", tool.description)
                    .put("parameters", schema)
                )
            )
        }

        val systemPrompt = """
            You are the Variable Planner for a plugin-based dynamic widget platform.
            Your task is to plan the variables and static assets required to render a user widget.
            
            You must determine:
            1. What data (variables) needs to be fetched, and which backend tools should fetch it.
            2. What static assets are needed.
            
            You should work in an agentic loop:
            - Discover available skills/tools in the "${routerOutput.domain}" domain using registry tools: "list_skills", "list_tools", "read_tool".
            - Call geocoding/search tools in this planning phase immediately to resolve location coordinates.
            - Once resolved, output the final plan.
            - Define dynamic variables with a tool-backed source (e.g. "/tool/weather/forecast") with parameters referencing location coordinates.
            
            Format of final plan response:
            {
              "status": "finish",
              "variables": [
                {
                  "variable_name": "location",
                  "variable_type": "object",
                  "source": {
                    "type": "static",
                    "value": {
                      "latitude": 42.3601,
                      "longitude": -71.0589,
                      "name": "Boston",
                      "country": "United States"
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
                      "longitude": "{{location.longitude}}"
                    }
                  }
                }
              ],
              "assets": ["/asset/weather/icons"]
            }
            
            Current Domain: ${routerOutput.domain}
        """.trimIndent()

        val messages = ArrayList<JSONObject>()
        messages.add(JSONObject().put("role", "system").put("content", systemPrompt))
        messages.add(JSONObject().put("role", "user").put("content", "Analyze this query and create a variable plan: \"$userQuery\""))

        val maxIterations = 8
        for (iteration in 0 until maxIterations) {
            val requestBody = JSONObject()
                .put("model", "gpt-4o")
                .put("messages", JSONArray(messages))
                .put("temperature", 0.1)
                .put("tools", toolDefinitions)

            val payloadStr = requestBody.toString()
            android.util.Log.d("llm_dbg", "LLM-2 Variable Planner Request (Iteration $iteration):\n$payloadStr")
            val responseStr = HttpUtil.post(
                urlStr = "https://api.openai.com/v1/chat/completions",
                jsonPayload = payloadStr,
                apiKey = apiKey,
                connectTimeoutMs = 30000,
                readTimeoutMs = 300000
            )
            android.util.Log.d("llm_dbg", "LLM-2 Variable Planner Response (Iteration $iteration):\n$responseStr")

            val responseJson = JSONObject(responseStr)
            val choice = responseJson.getJSONArray("choices").getJSONObject(0)
            val assistantMessage = choice.getJSONObject("message")
            val toolCalls = assistantMessage.optJSONArray("tool_calls")

            messages.add(assistantMessage)

            if (toolCalls == null || toolCalls.length() == 0) {
                var content = assistantMessage.optString("content", "{}").trim()
                val firstBrace = content.indexOf('{')
                val lastBrace = content.lastIndexOf('}')
                if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                    content = content.substring(firstBrace, lastBrace + 1)
                }
                return parsePlan(JSONObject(content.trim()))
            }

            for (i in 0 until toolCalls.length()) {
                val toolCall = toolCalls.getJSONObject(i)
                val callId = toolCall.getString("id")
                val fn = toolCall.getJSONObject("function")
                val fnName = fn.getString("name")
                val fnArgs = JSONObject(fn.optString("arguments", "{}"))

                val result = try {
                    when {
                        fnName == "list_skills" -> {
                            val domainName = fnArgs.optString("domain", routerOutput.domain)
                            val targetAdapter = DomainRegistry.getAdapter(domainName)
                            val skillsArray = JSONArray()
                            targetAdapter.getSkills().forEach {
                                skillsArray.put(JSONObject().put("path", it.path).put("name", it.name).put("description", it.description))
                            }
                            skillsArray
                        }
                        fnName == "list_tools" -> {
                            val domainName = fnArgs.optString("domain", routerOutput.domain)
                            val targetAdapter = DomainRegistry.getAdapter(domainName)
                            val toolsArray = JSONArray()
                            targetAdapter.getTools().forEach {
                                toolsArray.put(JSONObject().put("path", it.path).put("name", it.name).put("description", it.description))
                            }
                            toolsArray
                        }
                        fnName == "read_tool" -> {
                            val toolPath = fnArgs.getString("path")
                            val domainName = toolPath.substringAfter("/tool/").substringBefore("/")
                            val targetAdapter = DomainRegistry.getAdapter(domainName)
                            targetAdapter.readTool(toolPath)
                        }
                        fnName.startsWith("tool__") -> {
                            // Translate tool__weather__geocode back to /tool/weather/geocode
                            val toolPath = "/" + fnName.replace("__", "/")
                            adapter.executeTool(toolPath, fnArgs)
                        }
                        else -> JSONObject().put("error", "Unknown function $fnName")
                    }
                } catch (e: Exception) {
                    JSONObject().put("error", e.localizedMessage)
                }

                messages.add(JSONObject()
                    .put("role", "tool")
                    .put("tool_call_id", callId)
                    .put("name", fnName)
                    .put("content", result.toString())
                )
            }
        }

        throw Exception("Max iterations reached in Variable Planner.")
    }

    private fun parsePlan(json: JSONObject): VariablePlan {
        return VariablePlan(
            status = json.optString("status", "finish"),
            variables = json.optJSONArray("variables") ?: JSONArray(),
            assets = json.optJSONArray("assets") ?: JSONArray()
        )
    }
}
