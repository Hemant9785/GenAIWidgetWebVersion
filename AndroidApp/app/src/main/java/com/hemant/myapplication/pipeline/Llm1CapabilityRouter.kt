package com.hemant.myapplication.pipeline

import com.hemant.myapplication.domain.DomainRegistry
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

data class RouterOutput(
    val skills: List<String>,
    val tools: List<String>,
    val assets: List<String>,
    val isDecisionQuery: Boolean,
    val domain: String
)

class Llm1CapabilityRouter(private val apiKey: String) {
    @Throws(Exception::class)
    fun route(userQuery: String): RouterOutput {
        val allAdapters = DomainRegistry.getAllAdapters()
        val skillsSection = StringBuilder()
        val toolsSection = StringBuilder()
        val assetsSection = StringBuilder()

        allAdapters.forEach { adapter ->
            adapter.getSkills().forEach { skill ->
                skillsSection.append("- Coarse Path: \"${skill.path}\"\n  Domain: \"${adapter.domainName()}\"\n  Description: ${skill.description}\n")
            }
            adapter.getTools().forEach { tool ->
                toolsSection.append("- Coarse Path: \"${tool.path}\"\n  Domain: \"${adapter.domainName()}\"\n  Description: ${tool.description}\n")
            }
            adapter.getAssets().forEach { asset ->
                assetsSection.append("- Coarse Path: \"${asset.path}\"\n  Domain: \"${adapter.domainName()}\"\n  Description: ${asset.description}\n")
            }
        }

        val systemPrompt = """
            You are the Capability Router for a plugin-based dynamic widget platform.
            Your task is to analyze the user's query and route it to the correct domain by selecting the coarse, domain-level skill, tool, and asset paths.

            Available Domain Plugins are listed below:

            1. SKILLS (Domain-specific layout rules):
            $skillsSection

            2. TOOLS (Domain-specific API capabilities):
            $toolsSection

            3. ASSETS (Domain-specific static assets):
            $assetsSection

            Rules for Selection:
            - Select ONLY domain-level coarse paths listed above. Do NOT select specific files.
            - Only route to paths that are highly relevant to the query.
            - FALLBACK VIRTUAL DOMAIN: If the query does not match any of the available registry domain plugins listed above, you MUST route the query to the virtual "general" domain:
              * selected_paths.skills = ["/skill/general"]
              * selected_paths.tools = ["/tool/general"]
              * selected_paths.assets = []
              * domain = "generic"
              * is_decision_query = true
            - Determine if the query requires analysis, recommendations, advice, or suggestions based on data (e.g. "should I carry an umbrella?"). Set "is_decision_query" = true if it falls under these categories, else false.

            You MUST respond with a JSON object containing these keys:
            {
              "selected_paths": {
                "skills": ["/skill/weather"],
                "tools": ["/tool/weather"],
                "assets": ["/asset/weather"]
              },
              "domain": "weather",
              "confidence": 1.0,
              "reason": "explanation of routing choice",
              "is_decision_query": boolean
            }
        """.trimIndent()

        val requestBody = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray(listOf(
                JSONObject().put("role", "system").put("content", systemPrompt),
                JSONObject().put("role", "user").put("content", "User Query: \"$userQuery\"")
            )))

        val payloadStr = requestBody.toString()
        android.util.Log.d("llm_dbg", "LLM-1 Capability Router Request:\n$payloadStr")
        return try {
            val responseStr = HttpUtil.post(
                urlStr = "https://api.openai.com/v1/chat/completions",
                jsonPayload = payloadStr,
                apiKey = apiKey,
                connectTimeoutMs = 30000,
                readTimeoutMs = 300000
            )
            android.util.Log.d("llm_dbg", "LLM-1 Capability Router Response:\n$responseStr")
            val responseJson = JSONObject(responseStr)
            val contentStr = responseJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            val parsed = JSONObject(contentStr)
            val selectedPaths = parsed.optJSONObject("selected_paths") ?: JSONObject()
            val skills = jsonArrayToList(selectedPaths.optJSONArray("skills"))
            val tools = jsonArrayToList(selectedPaths.optJSONArray("tools"))
            val assets = jsonArrayToList(selectedPaths.optJSONArray("assets"))

            val domain = parsed.optString("domain", "generic")
            val isDecisionQuery = parsed.optBoolean("is_decision_query", false)

            RouterOutput(skills, tools, assets, isDecisionQuery, domain)
        } catch (e: Exception) {
            // Fallback
            RouterOutput(
                skills = listOf("/skill/general"),
                tools = listOf("/tool/general"),
                assets = emptyList(),
                isDecisionQuery = true,
                domain = "generic"
            )
        }
    }

    private fun jsonArrayToList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = ArrayList<String>()
        for (i in 0 until array.length()) {
            list.add(array.optString(i))
        }
        return list
    }
}
