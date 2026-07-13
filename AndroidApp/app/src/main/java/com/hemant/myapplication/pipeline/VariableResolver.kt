package com.hemant.myapplication.pipeline

import com.hemant.myapplication.domain.DomainAdapter
import org.json.JSONObject

data class ResolvedWidgetData(
    val runtimeValues: JSONObject,
    val variableDefinitions: List<VariableDefinition>,
)

class VariableResolver(private val adapter: DomainAdapter) {
    private val resolvedMap = HashMap<String, Any>()
    private val resolvingSet = HashSet<String>()

    @Throws(Exception::class)
    suspend fun resolve(plan: VariablePlan): ResolvedWidgetData {
        val variablesMap = HashMap<String, JSONObject>()
        for (i in 0 until plan.variables.length()) {
            val variable = plan.variables.getJSONObject(i)
            val name = variable.getString("variable_name")
            variablesMap[name] = variable
        }

        // Clear previous state
        resolvedMap.clear()
        resolvingSet.clear()

        // Resolve all variables on demand
        for (name in variablesMap.keys) {
            resolveVariable(name, variablesMap)
        }

        // Consolidate resolved values into a unified global model
        val model = JSONObject()
        for (name in variablesMap.keys) {
            val value = resolvedMap[name]
            val sourceType = variablesMap.getValue(name)
                .getJSONObject("source")
                .optString("type")
            if (sourceType == "tool" && value is JSONObject) {
                val innerModel = value.optJSONObject("model")
                if (innerModel != null) {
                    // Merge internal model mappings (like weather forecast payloads) directly into root
                    val keys = innerModel.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        model.put(key, innerModel.opt(key))
                    }
                } else {
                    model.put(name, value)
                }
            }
            // Static values always retain their declared variable name, so a
            // planner value such as `summary` is reliably bound at
            // /model/summary/... regardless of its internal object keys.
            else model.put(name, value)
        }

        return ResolvedWidgetData(
            runtimeValues = JSONObject().put("model", model),
            variableDefinitions = plan.definitions,
        )
    }

    @Throws(Exception::class)
    private suspend fun resolveVariable(name: String, variablesMap: Map<String, JSONObject>) {
        if (resolvedMap.containsKey(name)) return

        if (resolvingSet.contains(name)) {
            throw Exception("Circular dependency cycle detected for variable '$name'")
        }
        resolvingSet.add(name)

        val variable = variablesMap[name] ?: throw Exception("Variable '$name' not found in plan")
        val source = variable.getJSONObject("source")
        val type = source.optString("type")

        if (type == "static") {
            if (!source.has("value") || source.isNull("value")) {
                throw Exception("Static variable '$name' is missing a value")
            }
            resolvedMap[name] = source.opt("value")
        } else if (type == "tool") {
            val toolPath = source.getString("tool_path")
            val rawParams = source.optJSONObject("parameters") ?: JSONObject()
            
            // Recursively resolve all parameter references first
            val resolvedParams = resolveParameters(rawParams, variablesMap)

            // Execute the dynamic domain tool
            val result = adapter.executeTool(toolPath, resolvedParams)
            if (result.has("error")) {
                throw Exception("Tool '$toolPath' execution failed: ${result.getString("error")}")
            }
            resolvedMap[name] = result
        } else {
            throw Exception("Variable '$name' has unsupported source type '$type'")
        }

        resolvingSet.remove(name)
    }

    private suspend fun resolveParameters(params: JSONObject, variablesMap: Map<String, JSONObject>): JSONObject {
        val result = JSONObject()
        val keys = params.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = params.opt(key)
            if (value is String && value.startsWith("{{") && value.endsWith("}}")) {
                val expression = value.substring(2, value.length - 2).trim()
                val parts = expression.split(".")
                if (parts.size >= 2) {
                    val varName = parts[0]
                    val fieldName = parts[1]
                    
                    // Resolve dependency variable recursively
                    resolveVariable(varName, variablesMap)
                    
                    val resolvedVar = resolvedMap[varName] as? JSONObject
                    if (resolvedVar != null) {
                        // Preserves original parameter types (e.g. Double/Float coordinates)
                        result.put(key, resolvedVar.opt(fieldName))
                    } else {
                        result.put(key, value)
                    }
                } else {
                    result.put(key, value)
                }
            } else {
                result.put(key, value)
            }
        }
        return result
    }
}
