package com.hemant.myapplication.domain

import com.hemant.myapplication.model.WidgetDocument
import org.json.JSONObject

class GenericDomainAdapter : DomainAdapter {
    override fun domainName(): String = "generic"
    override fun categoryName(): String = "general"

    override fun getSystemPromptGuidance(): String {
        return """
            GENERIC WIDGET SPECIFICS:
            - Fulfill the request with one or more STATIC variables generated from general knowledge. Do not use tools.
            - Every static variable MUST include a non-empty `description`. The description is internal metadata for the layout generator: explain the meaning of the data, its hierarchy, and the most useful visual representation.
            - Add `presentation_hints` when useful, such as "summary", "comparison", "ranked_list", "timeline", "checklist", "metrics", or "cards".
            - Put only user-facing values inside `source.value`. Never put the internal description or presentation hints in `source.value`.
            - Use structured arrays of objects for lists, comparisons, steps, timelines, recommendations, and tables so the renderer can bind an InsightList.
            - Do not claim static knowledge is live or current. Queries that need current prices, weather, news, scores, availability, or other time-sensitive facts must be handled by a live-data domain/tool instead.
        """.trimIndent()
    }

    override fun getSkills(): List<SkillSummary> {
        return listOf(
            SkillSummary(
                path = "/skill/general",
                name = "General Skill",
                description = "Fulfills any general query requests using text, lists, or static image grids."
            )
        )
    }

    override fun getTools(): List<ToolSummary> = emptyList()

    override fun getAssets(): List<AssetSummary> = emptyList()

    override fun readTool(toolPath: String): JSONObject = JSONObject()

    override fun readToolResponseSchema(toolPath: String): JSONObject = JSONObject()

    override suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject {
        val model = params.optJSONObject("model") ?: JSONObject()
        return JSONObject().put("model", model)
    }
}
