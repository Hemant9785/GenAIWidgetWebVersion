package com.hemant.myapplication.pipeline

import com.hemant.myapplication.model.GenWidgetSpecParser
import com.hemant.myapplication.model.BindingExpr
import com.hemant.myapplication.model.ComponentValue
import com.hemant.myapplication.model.WidgetComponentCatalog
import com.hemant.myapplication.model.WidgetDocument
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

class Llm3LayoutGenerator(private val apiKey: String) {
    @Throws(Exception::class)
    fun generateLayout(
        userQuery: String,
        resolvedVariables: JSONObject,
        variableDefinitions: List<VariableDefinition>,
        domain: String,
    ): WidgetDocument {
        val variableMetadata = JSONArray().also { output ->
            variableDefinitions.forEach { definition -> output.put(definition.toJson()) }
        }.toString(2)
        val systemPrompt = """
            You are the Layout Generator (LLM-3) for a dynamic Android widget platform.
            Your task is to design a beautiful, modern Android widget layout tree represented as a GenWidget-A2UI JSONSpec document.
            Return ONLY a valid JSON object. Do not wrap in markdown tags like ```json ... ```. No comments or explanations.
            
            Top-Level Schema Structure:
            - schemaVersion: "0.1"
            - kind: "genWidget"
            - widgetId: "${domain}_widget"
            - metadata: { "title": "${domain.replaceFirstChar { it.uppercase() }} Widget", "category": "$domain" }
            - supportedSizes: { "default": "4x3", "breakpoints": ["4x3"] }
            - stateMachine: { "states": { "ready": { "screen": "screen.ready" } } }
            - screens: { "screen.ready": { "layouts": { "4x3": "surface.ready.4x3" } } }
            - a2uiSurfaces: {
                "surface.ready.4x3": {
                    "version": "v1.0",
                    "createSurface": {
                        "surfaceId": "surface.ready.4x3",
                        "catalogId": "genwidget://catalog/android-widget-v1",
                        "root": "root_column",
                        "components": [
                            // Flat list of components
                        ]
                    }
                }
              }
            - preview: {
                "mockData": $resolvedVariables
              }
              
            Component Rules:
            - Every component must have a unique "id" (string), "component" (string), "fields" (object of properties), and "children" (array of child component IDs).
            - Do not nest components. Write a flat array of components. A component links to its children by their IDs.
            - You may use ONLY this renderer-supported catalog:
            ${WidgetComponentCatalog.promptCatalog()}
              
            BindingExpr Options:
            - Binding to path: { "path": "/model/..." }
            - Format String: { "formatString": "Text {val}", "args": { "val": { "path": "/model/..." } } }
            - Literal String: { "literalString": "Static Text" }
            - Literal Number: { "literalNumber": 23.4 }
            
            How to display array lists horizontally:
            - Use the "InsightList" component.
            - Set "source" field to the array path (e.g. { "path": "/model/weather/hourlyItemsToday" }).
            - Set "presentation" to "chips" to display it as a horizontal grid of chip items, or set "layout" to "horizontal".
            
            Runtime values available for visible bindings:
            $resolvedVariables

            INTERNAL VARIABLE DEFINITIONS FOR LAYOUT REASONING ONLY:
            $variableMetadata

            Use the internal descriptions and presentation hints to choose visual hierarchy and component type. They are not user-facing data.
            NEVER display a variable description or presentation hint as text, a literal string, an action label, or preview data. Bind visible content only to paths that exist in the runtime values above, such as `/model/<variable_name>/...`.
            Do not invent paths, components, or fields outside the supplied catalog.
            
            Few-Shot Flat JSON Examples:
            
            Example 1: Weather widget displaying horizontal forecast chips (Weather Domain)
            {
              "schemaVersion": "0.1",
              "kind": "genWidget",
              "widgetId": "weather_widget",
              "metadata": { "title": "Weather Widget", "category": "weather" },
              "supportedSizes": { "default": "4x3" },
              "stateMachine": { "states": { "ready": { "screen": "screen.ready" } } },
              "screens": { "screen.ready": { "layouts": { "4x3": "surface.ready.4x3" } } },
              "a2uiSurfaces": {
                "surface.ready.4x3": {
                  "version": "v1.0",
                  "createSurface": {
                    "surfaceId": "surface.ready.4x3",
                    "catalogId": "genwidget://catalog/android-widget-v1",
                    "root": "root_column",
                    "components": [
                      {
                        "id": "root_column",
                        "component": "Column",
                        "fields": { "gap": "md", "padding": "md", "background": "surface", "cornerRadius": "lg" },
                        "children": ["header_row", "temp_text", "insight_list"]
                      },
                      {
                        "id": "header_row",
                        "component": "Row",
                        "fields": { "gap": "sm", "align": "spaceBetween" },
                        "children": ["title_text", "condition_icon"]
                      },
                      {
                        "id": "title_text",
                        "component": "Text",
                        "fields": { "text": { "path": "/model/weather/location" }, "style": "titleMedium", "color": "primary" },
                        "children": []
                      },
                      {
                        "id": "condition_icon",
                        "component": "Icon",
                        "fields": { "icon": { "path": "/model/weather/conditionIcon" }, "size": "md" },
                        "children": []
                      },
                      {
                        "id": "temp_text",
                        "component": "Text",
                        "fields": { "text": { "path": "/model/weather/temperatureText" }, "style": "displaySmall", "color": "primary" },
                        "children": []
                      },
                      {
                        "id": "insight_list",
                        "component": "InsightList",
                        "fields": { "source": { "path": "/model/weather/hourlyItemsToday" }, "presentation": "chips", "maxItems": 4 },
                        "children": []
                      }
                    ]
                  }
                }
              },
              "preview": {
                "mockData": {}
              }
            }
            
            Example 2: Informational widget with static title and content (Generic Domain)
            {
              "schemaVersion": "0.1",
              "kind": "genWidget",
              "widgetId": "generic_widget",
              "metadata": { "title": "Info Widget", "category": "generic" },
              "supportedSizes": { "default": "4x3" },
              "stateMachine": { "states": { "ready": { "screen": "screen.ready" } } },
              "screens": { "screen.ready": { "layouts": { "4x3": "surface.ready.4x3" } } },
              "a2uiSurfaces": {
                "surface.ready.4x3": {
                  "version": "v1.0",
                  "createSurface": {
                    "surfaceId": "surface.ready.4x3",
                    "catalogId": "genwidget://catalog/android-widget-v1",
                    "root": "root_column",
                    "components": [
                      {
                        "id": "root_column",
                        "component": "Column",
                        "fields": { "gap": "sm", "padding": "md", "background": "surface", "cornerRadius": "md" },
                        "children": ["title_text", "divider", "content_text"]
                      },
                      {
                        "id": "title_text",
                        "component": "Text",
                        "fields": { "text": { "path": "/model/summary/title" }, "style": "titleLarge", "color": "primary" },
                        "children": []
                      },
                      {
                        "id": "divider",
                        "component": "Divider",
                        "fields": {},
                        "children": []
                      },
                      {
                        "id": "content_text",
                        "component": "Text",
                        "fields": { "text": { "path": "/model/summary/content" }, "style": "bodySmall", "color": "secondary" },
                        "children": []
                      }
                    ]
                  }
                }
              },
              "preview": {
                "mockData": {}
              }
            }
        """.trimIndent()

        val requestBody = JSONObject()
            .put("model", "gpt-4o")
            .put("response_format", JSONObject().put("type", "json_object"))
            .put("messages", JSONArray(listOf(
                JSONObject().put("role", "system").put("content", systemPrompt),
                JSONObject().put("role", "user").put("content", "User Query: \"$userQuery\"")
            )))

        val payloadStr = requestBody.toString()
        android.util.Log.d("llm_dbg", "LLM-3 Layout Generator Request:\n$payloadStr")
        val responseStr = HttpUtil.post(
            urlStr = "https://api.openai.com/v1/chat/completions",
            jsonPayload = payloadStr,
            apiKey = apiKey,
            connectTimeoutMs = 30000,
            readTimeoutMs = 300000
        )
        android.util.Log.d("llm_dbg", "LLM-3 Layout Generator Response:\n$responseStr")

        val responseJson = JSONObject(responseStr)
        val choices = responseJson.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            throw Exception("OpenAI LLM-3 returned no choices.")
        }
        val message = choices.optJSONObject(0).optJSONObject("message")
        var content = message?.optString("content")?.trim().orEmpty()
        
        // Safety strip of markdown wrappers
        val firstBrace = content.indexOf('{')
        val lastBrace = content.lastIndexOf('}')
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            content = content.substring(firstBrace, lastBrace + 1)
        }
        
        android.util.Log.d("HEMANT_DBG", "Raw LLM-3 Layout Spec JSON:\n${content.trim()}")
        
        return GenWidgetSpecParser.parse(content.trim()).also { document ->
            ensurePlanningMetadataIsHidden(document, variableDefinitions)
        }
    }

    private fun ensurePlanningMetadataIsHidden(
        document: WidgetDocument,
        variableDefinitions: List<VariableDefinition>,
    ) {
        val internalDescriptions = variableDefinitions
            .map { normalize(it.description) }
            .filter { it.length >= 24 }
            .distinct()
        if (internalDescriptions.isEmpty()) return

        fun includesInternalDescription(text: String): Boolean {
            val normalizedText = normalize(text)
            return internalDescriptions.any { description -> normalizedText.contains(description) }
        }

        fun inspect(value: ComponentValue) {
            when (value) {
                is ComponentValue.Text -> require(!includesInternalDescription(value.value)) {
                    "Layout attempted to display internal variable metadata"
                }
                is ComponentValue.Binding -> {
                    val expr = value.expr
                    if (expr is BindingExpr.LiteralString) {
                        require(!includesInternalDescription(expr.value)) {
                            "Layout attempted to display internal variable metadata"
                        }
                    }
                }
                is ComponentValue.ListValue -> value.values.forEach(::inspect)
                is ComponentValue.ObjectValue -> value.values.values.forEach(::inspect)
                else -> Unit
            }
        }

        document.surfaces.values.forEach { surface ->
            surface.components.values.forEach { component ->
                component.fields.values.forEach(::inspect)
            }
        }
        document.previewMockData?.let { preview ->
            require(!includesInternalDescription(preview.toString())) {
                "Layout preview data contains internal variable metadata"
            }
        }
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()
}
