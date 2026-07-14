package com.hemant.myapplication.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hemant.myapplication.domain.AssetSummary
import com.hemant.myapplication.domain.DomainAdapter
import com.hemant.myapplication.domain.SkillSummary
import com.hemant.myapplication.domain.ToolSummary
import com.hemant.myapplication.location.CurrentLocationProvider
import com.hemant.myapplication.location.CurrentLocationResult
import com.hemant.myapplication.tools.DefaultToolRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VariableResolverPrivacyTest {
    @Test
    fun internalDefaultToolResultIsExcludedFromRuntimeModel() = runBlocking {
        val defaultTools = DefaultToolRegistry(
            object : CurrentLocationProvider {
                override suspend fun getCurrentLocation(): CurrentLocationResult =
                    CurrentLocationResult.Available(12.9716, 77.5946, 100f)
            },
        )
        val plan = VariablePlan(
            status = "finish",
            variables = JSONArray().put(
                JSONObject()
                    .put("variable_name", "currentLocation")
                    .put("variable_type", "object")
                    .put("description", "Private device coordinates used by another tool.")
                    .put("exposure", "internal")
                    .put("source", JSONObject()
                        .put("type", "tool")
                        .put("tool_path", DefaultToolRegistry.CURRENT_LOCATION_PATH)
                        .put("parameters", JSONObject()),
                    ),
            ),
            assets = JSONArray(),
            definitions = listOf(
                VariableDefinition(
                    name = "currentLocation",
                    type = "object",
                    description = "Private device coordinates used by another tool.",
                    exposure = VariableDefinition.EXPOSURE_INTERNAL,
                ),
            ),
        )

        val resolved = VariableResolver(EmptyDomainAdapter, defaultTools).resolve(plan)

        assertFalse(resolved.runtimeValues.getJSONObject("model").has("currentLocation"))
    }

    private object EmptyDomainAdapter : DomainAdapter {
        override fun domainName(): String = "test"
        override fun categoryName(): String = "test"
        override fun getSystemPromptGuidance(): String = ""
        override fun getSkills(): List<SkillSummary> = emptyList()
        override fun getTools(): List<ToolSummary> = emptyList()
        override fun getAssets(): List<AssetSummary> = emptyList()
        override fun readTool(toolPath: String): JSONObject = JSONObject()
        override fun readToolResponseSchema(toolPath: String): JSONObject = JSONObject()
        override suspend fun executeTool(toolPath: String, params: JSONObject): JSONObject =
            JSONObject().put("error", "Unexpected domain tool call")
    }
}
