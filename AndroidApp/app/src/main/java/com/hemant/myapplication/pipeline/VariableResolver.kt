package com.hemant.myapplication.pipeline

import com.hemant.myapplication.domain.DomainAdapter
import org.json.JSONObject

class VariableResolver(private val adapter: DomainAdapter) {
    private val resolvedMap = HashMap<String, Any>()

    @Throws(Exception::class)
    suspend fun resolve(plan: VariablePlan): JSONObject {
        // Resolve static variables first
        for (i in 0 until plan.variables.length()) {
            val variable = plan.variables.getJSONObject(i)
            val name = variable.getString("variable_name")
            val source = variable.getJSONObject("source")
            val type = source.optString("type")
            if (type == "static") {
                val value = source.opt("value")
                if (value != null) {
                    resolvedMap[name] = value
                }
            }
        }

        // Resolve tool-backed variables next
        for (i in 0 until plan.variables.length()) {
            val variable = plan.variables.getJSONObject(i)
            val name = variable.getString("variable_name")
            val source = variable.getJSONObject("source")
            val type = source.optString("type")
            if (type == "tool") {
                val toolPath = source.getString("tool_path")
                val rawParams = source.optJSONObject("parameters") ?: JSONObject()
                val resolvedParams = resolveParameters(rawParams)

                // Retrieve location details from previously resolved static location variable
                val locObj = resolvedMap["location"] as? JSONObject
                if (locObj != null) {
                    val keys = locObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (!resolvedParams.has(key)) {
                            resolvedParams.put(key, locObj.opt(key))
                        }
                    }
                }

                return adapter.executeTool(toolPath, resolvedParams)
            }
        }

        return JSONObject().put("model", JSONObject())
    }

    private fun resolveParameters(params: JSONObject): JSONObject {
        val result = JSONObject()
        val keys = params.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = params.optString(key)
            if (value.startsWith("{{") && value.endsWith("}}")) {
                val expression = value.substring(2, value.length - 2).trim()
                val parts = expression.split(".")
                if (parts.size >= 2) {
                    val varName = parts[0]
                    val fieldName = parts[1]
                    val resolvedVar = resolvedMap[varName] as? JSONObject
                    if (resolvedVar != null) {
                        result.put(key, resolvedVar.opt(fieldName))
                    } else {
                        result.put(key, value)
                    }
                } else {
                    result.put(key, value)
                }
            } else {
                result.put(key, params.opt(key))
            }
        }
        return result
    }
}
