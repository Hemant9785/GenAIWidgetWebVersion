package com.hemant.myapplication.pipeline

import org.json.JSONArray
import org.json.JSONObject

/**
 * Semantic metadata for a runtime variable. It is supplied to the layout
 * generator only; it is deliberately kept out of the runtime model that the
 * renderer can bind to.
 */
data class VariableDefinition(
    val name: String,
    val type: String,
    val description: String,
    val presentationHints: List<String> = emptyList(),
) {
    fun toJson(): JSONObject {
        val hints = JSONArray()
        presentationHints.forEach(hints::put)
        return JSONObject()
            .put("variable_name", name)
            .put("variable_type", type)
            .put("description", description)
            .put("presentation_hints", hints)
    }

    companion object {
        fun fromPlanVariable(variable: JSONObject): VariableDefinition {
            val hints = variable.optJSONArray("presentation_hints")
                ?: variable.optJSONArray("presentationHints")
            return VariableDefinition(
                name = variable.optString("variable_name"),
                type = variable.optString("variable_type", "object"),
                description = variable.optString("description"),
                presentationHints = hints?.let { array ->
                    List(array.length()) { index -> array.optString(index) }
                        .filter { it.isNotBlank() }
                }.orEmpty(),
            )
        }
    }
}

data class VariablePlan(
    val status: String,
    val variables: JSONArray,
    val assets: JSONArray,
    val definitions: List<VariableDefinition> = emptyList(),
)
