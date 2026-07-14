package com.hemant.myapplication.pipeline

import android.util.Log
import com.hemant.myapplication.domain.DomainAdapter
import com.hemant.myapplication.domain.DomainRegistry
import com.hemant.myapplication.tools.DefaultToolRegistry
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

data class RouterOutput(
    val skills: List<String>,
    val tools: List<String>,
    val assets: List<String>,
    val isDecisionQuery: Boolean,
    val domain: String,
)

class Llm1CapabilityRouter(
    private val apiKey: String,
    private val defaultTools: DefaultToolRegistry,
) {
    @Throws(Exception::class)
    fun route(userQuery: String): RouterOutput {
        val adapters = DomainRegistry.getAllAdapters()
        val systemPrompt = buildSystemPrompt(adapters)
        var correction: String? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val content = requestRoute(systemPrompt, userQuery, correction)
                return parseAndValidate(content, adapters)
            } catch (error: Exception) {
                Log.w(TAG, "Router response rejected (attempt ${attempt + 1}/$MAX_ATTEMPTS): ${error.message}")
                correction = "Your previous router response was invalid: ${error.message}. " +
                    "Return only a new object that exactly matches the required schema. " +
                    "Put domain at the root, never inside selected_paths."
            }
        }

        // A network/model/schema failure must not fabricate paths or silently default to
        // generic. Use the registry's deterministic classifier and only registered paths.
        val adapter = DomainRegistry.classify(userQuery)
        Log.w(TAG, "Router used deterministic fallback domain='${adapter.domainName()}' after $MAX_ATTEMPTS invalid responses")
        return deterministicOutput(adapter)
    }

    private fun buildSystemPrompt(adapters: List<DomainAdapter>): String {
        val skillsSection = StringBuilder()
        val toolsSection = StringBuilder()
        val assetsSection = StringBuilder()

        defaultTools.getTools().forEach { tool ->
            toolsSection.append("- Default Path: \"${tool.path}\"\n  Description: ${tool.description}\n")
        }
        adapters.forEach { adapter ->
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

        return """
            You are the Capability Router for a plugin-based dynamic widget platform.
            Select the single matching domain and only registered coarse paths.

            SKILLS:
            $skillsSection

            TOOLS:
            $toolsSection

            ASSETS:
            $assetsSection

            Rules:
            - Default tools are globally available, but do not change the selected domain.
            - A non-default path must belong to the selected domain.
            - Use the `generic` domain only when no listed live-data domain fits.
            - `domain` is a REQUIRED TOP-LEVEL field, never a member of `selected_paths`.
            - `selected_paths` contains only `skills`, `tools`, and `assets` arrays.
        """.trimIndent()
    }

    private fun requestRoute(systemPrompt: String, userQuery: String, correction: String?): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", "User Query: \"$userQuery\""))
        correction?.let { messages.put(JSONObject().put("role", "user").put("content", it)) }

        val requestBody = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("temperature", 0)
            .put("response_format", routerResponseFormat())
            .put("messages", messages)
        val payload = requestBody.toString()
        Log.d("llm_dbg", "LLM-1 Capability Router Request:\n$payload")

        val response = HttpUtil.post(
            urlStr = "https://api.openai.com/v1/chat/completions",
            jsonPayload = payload,
            apiKey = apiKey,
            connectTimeoutMs = 30_000,
            readTimeoutMs = 300_000,
        )
        Log.d("llm_dbg", "LLM-1 Capability Router Response:\n$response")
        return JSONObject(response)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .optString("content")
            .ifBlank { throw Exception("Router returned an empty response") }
    }

    private fun parseAndValidate(content: String, adapters: List<DomainAdapter>): RouterOutput {
        val parsed = JSONObject(content)
        val selectedPaths = parsed.optJSONObject("selected_paths")
            ?: throw Exception("Missing object 'selected_paths'")

        // Compatibility recovery for the exact malformed JSON seen in the log. Strict
        // structured output prevents new responses from taking this shape.
        if (!parsed.has("domain") && selectedPaths.has("domain")) {
            parsed.put("domain", selectedPaths.getString("domain"))
            selectedPaths.remove("domain")
            Log.w(TAG, "Recovered legacy nested 'domain' from selected_paths")
        }

        require(parsed.has("domain")) { "Missing top-level field 'domain'" }
        require(parsed.has("is_decision_query")) { "Missing top-level field 'is_decision_query'" }
        require(selectedPaths.has("skills") && selectedPaths.has("tools") && selectedPaths.has("assets")) {
            "selected_paths must contain skills, tools, and assets arrays"
        }

        val domain = parsed.getString("domain")
        val adapter = adapters.firstOrNull { it.domainName() == domain }
            ?: throw Exception("Unregistered domain '$domain'")
        val skills = jsonArrayToList(selectedPaths.getJSONArray("skills"), "skills")
        val tools = jsonArrayToList(selectedPaths.getJSONArray("tools"), "tools")
        val assets = jsonArrayToList(selectedPaths.getJSONArray("assets"), "assets")

        val domainSkills = adapter.getSkills().mapTo(HashSet()) { it.path }
        val domainTools = adapter.getTools().mapTo(HashSet()) { it.path }
        val domainAssets = adapter.getAssets().mapTo(HashSet()) { it.path }
        val defaultToolPaths = defaultTools.getTools().mapTo(HashSet()) { it.path }
        require(skills.all(domainSkills::contains)) { "Skills do not belong to domain '$domain'" }
        require(assets.all(domainAssets::contains)) { "Assets do not belong to domain '$domain'" }
        require(tools.all { it in domainTools || it in defaultToolPaths }) { "Tools do not belong to domain '$domain'" }

        return RouterOutput(
            skills = skills,
            tools = tools,
            assets = assets,
            isDecisionQuery = parsed.getBoolean("is_decision_query"),
            domain = domain,
        )
    }

    private fun deterministicOutput(adapter: DomainAdapter): RouterOutput = RouterOutput(
        skills = adapter.getSkills().map { it.path },
        tools = adapter.getTools().map { it.path },
        assets = adapter.getAssets().map { it.path },
        isDecisionQuery = adapter.domainName() == "generic",
        domain = adapter.domainName(),
    )

    private fun routerResponseFormat(): JSONObject = JSONObject()
        .put("type", "json_schema")
        .put("json_schema", JSONObject()
            .put("name", "capability_router")
            .put("strict", true)
            .put("schema", JSONObject()
                .put("type", "object")
                .put("additionalProperties", false)
                .put("properties", JSONObject()
                    .put("selected_paths", JSONObject()
                        .put("type", "object")
                        .put("additionalProperties", false)
                        .put("properties", JSONObject()
                            .put("skills", stringArraySchema())
                            .put("tools", stringArraySchema())
                            .put("assets", stringArraySchema())
                        )
                        .put("required", JSONArray(listOf("skills", "tools", "assets")))
                    )
                    .put("domain", JSONObject().put("type", "string").put("enum", JSONArray(DomainRegistry.getAllAdapters().map { it.domainName() })))
                    .put("confidence", JSONObject().put("type", "number"))
                    .put("reason", JSONObject().put("type", "string"))
                    .put("is_decision_query", JSONObject().put("type", "boolean"))
                )
                .put("required", JSONArray(listOf("selected_paths", "domain", "confidence", "reason", "is_decision_query")))
            )
        )

    private fun stringArraySchema(): JSONObject = JSONObject()
        .put("type", "array")
        .put("items", JSONObject().put("type", "string"))

    private fun jsonArrayToList(array: JSONArray, name: String): List<String> =
        List(array.length()) { index ->
            require(array.opt(index) is String) { "selected_paths.$name must contain only strings" }
            array.getString(index)
        }

    private companion object {
        const val TAG = "RouterDebug"
        const val MAX_ATTEMPTS = 2
    }
}
