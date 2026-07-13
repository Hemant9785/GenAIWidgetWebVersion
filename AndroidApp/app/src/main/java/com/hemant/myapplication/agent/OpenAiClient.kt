package com.hemant.myapplication.agent

import com.hemant.myapplication.domain.DomainAdapter
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

class OpenAiClient(private val apiKey: String) {
    @Throws(Exception::class)
    fun generateSpec(userPrompt: String, adapter: DomainAdapter): String {
        val systemPrompt = PromptBuilder.buildSystemPrompt(adapter)
        
        val requestBody = JSONObject()
            .put("model", "gpt-4o-mini")
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray(listOf(
                JSONObject().put("role", "system").put("content", systemPrompt),
                JSONObject().put("role", "user").put("content", userPrompt)
            )))
            
        val responseStr = HttpUtil.post(
            urlStr = "https://api.openai.com/v1/chat/completions",
            jsonPayload = requestBody.toString(),
            apiKey = apiKey
        )
        
        val responseJson = JSONObject(responseStr)
        val choices = responseJson.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw Exception("OpenAI returned no completions choices.")
        }
        val message = choices.optJSONObject(0).optJSONObject("message")
        var content = message?.optString("content")?.trim().orEmpty()
        
        // Safety strip of markdown wrappers
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1)
        }
        return content.trim()
    }
}
