package com.hemant.myapplication.domain

import com.hemant.myapplication.model.WidgetDocument
import org.json.JSONObject

data class SkillSummary(val path: String, val name: String, val description: String)
data class ToolSummary(val path: String, val name: String, val description: String)
data class AssetSummary(val path: String, val name: String, val description: String)

interface DomainAdapter {
    fun domainName(): String
    fun categoryName(): String
    fun getSystemPromptGuidance(): String
    
    fun getSkills(): List<SkillSummary>
    fun getTools(): List<ToolSummary>
    fun getAssets(): List<AssetSummary>
    
    fun readTool(toolPath: String): JSONObject
    suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject
}
