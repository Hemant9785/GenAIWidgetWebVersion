package com.hemant.myapplication.pipeline

import com.hemant.myapplication.model.GenWidgetSpecParser
import com.hemant.myapplication.model.WidgetDocument
import com.hemant.myapplication.util.HttpUtil
import org.json.JSONArray
import org.json.JSONObject

class Llm3LayoutGenerator(private val apiKey: String) {
    @Throws(Exception::class)
    fun generateLayout(userQuery: String, resolvedVariables: JSONObject, domain: String): WidgetDocument {
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
            - Every component must have a unique "id" (string), "component" (string, e.g. Column, Row, Text, Icon, Spacer, Divider, Image, InsightList), "fields" (object of properties), and "children" (array of child component IDs).
            - Do not nest components. Write a flat array of components. A component links to its children by their IDs.
            - Allowed components:
              * Column: fields: "gap" ("xs", "sm", "md", "lg"), "padding" ("xs", "sm", "lg", "md"), "background" ("surface", "transparent"), "cornerRadius" ("sm", "md", "lg")
              * Row: fields: "gap", "align" ("center", "end", "spaceBetween")
              * Text: fields: "text" (BindingExpr), "style" ("titleLarge", "titleMedium", "bodySmall", "labelSmall", "displaySmall"), "color" ("primary", "secondary", "muted", "warning")
              * Icon: fields: "icon" (IconRequest), "size" ("xs", "sm", "md", "lg", "xl"), "color" ("primary", "secondary", "muted", "warning")
              * IconButton: fields: "icon" (IconRequest), "background" ("transparent", "default")
              * Image: fields: "source" (BindingExpr)
              * Spacer: fields: "size" ("xs", "sm", "md", "lg", "xl")
              * Divider
              * InsightList: fields: "source" (BindingExpr.Path), "presentation" ("chips", "list"), "layout" ("horizontal", "vertical"), "maxItems" (number), "columns" (number)
              
            BindingExpr Options:
            - Binding to path: { "path": "/model/..." }
            - Format String: { "formatString": "Text {val}", "args": { "val": { "path": "/model/..." } } }
            - Literal String: { "literalString": "Static Text" }
            - Literal Number: { "literalNumber": 23.4 }
            
            How to display array lists horizontally:
            - Use the "InsightList" component.
            - Set "source" field to the array path (e.g. { "path": "/model/weather/hourlyItemsToday" }).
            - Set "presentation" to "chips" to display it as a horizontal grid of chip items, or set "layout" to "horizontal".
            
            Live Variables Snapshot available at runtime:
            $resolvedVariables
            
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
                        "fields": { "text": { "path": "/model/genericInfo/title" }, "style": "titleLarge", "color": "primary" },
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
                        "fields": { "text": { "path": "/model/genericInfo/content" }, "style": "bodySmall", "color": "secondary" },
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
        
        val doc = GenWidgetSpecParser.parse(content.trim())
        return doc
    }
}
