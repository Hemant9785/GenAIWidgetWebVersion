package com.hemant.myapplication.domain

import com.hemant.myapplication.model.WidgetDocument
import org.json.JSONObject

class GenericDomainAdapter : DomainAdapter {
    override fun domainName(): String = "generic"
    override fun categoryName(): String = "general"

    override fun getSystemPromptGuidance(): String {
        return """
            GENERIC WIDGET SPECIFICS:
            - The widget must fulfill the user's request using static values or general bindings.
            - You can bind to "/model/generic/title", "/model/generic/content", "/model/generic/subtitle", or write literals directly using { "literalString": "..." }.
            - Create a beautiful layout using Columns, Rows, Texts, Dividers, and Icons that is visual and informative.
            - Populate the 'preview.mockData' with details matching the user's custom prompt.
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

    override suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject {
        val model = params.optJSONObject("model") ?: JSONObject()
        return JSONObject().put("model", model)
    }
}
