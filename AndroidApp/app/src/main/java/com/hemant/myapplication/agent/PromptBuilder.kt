package com.hemant.myapplication.agent

import com.hemant.myapplication.domain.DomainAdapter

object PromptBuilder {
    fun buildSystemPrompt(adapter: DomainAdapter): String {
        val basePrompt = """
            You are a dynamic Android widget generator that generates GenWidget-A2UI JSONSpec documents.
            Return ONLY a valid JSON object. Do not wrap in ```json ... ``` blocks, do not write explanations, comments, or any HTML/SVG/Kotlin code.
            
            Top-Level Keys:
            - schemaVersion: "0.1"
            - kind: "genWidget"
            - widgetId: "${adapter.domainName()}_widget"
            - metadata: { "title": "${adapter.domainName().replaceFirstChar { it.uppercase() }} Widget", "category": "${adapter.categoryName()}" }
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
                "mockData": {
                   // Mock values representing the resolved model snapshot
                }
              }
              
            Component Rules:
            - Every component must have a unique "id" (string), "component" (string, e.g. Column, Row, Text, Icon, Spacer, Divider, Image), "fields" (object of properties), and "children" (array of child component IDs).
            - Do not nest components. Write a flat array of components. A component links to its children by their IDs.
            - Allowed components:
              * Column: fields: "gap" ("xs", "sm", "md", "lg"), "padding" ("xs", "sm", "lg", "md"), "background" ("surface", "transparent"), "cornerRadius" ("sm", "md", "lg")
              * Row: fields: "gap", "align" ("center", "end", "spaceBetween")
              * Text: fields: "text" (BindingExpr), "style" ("titleLarge", "titleMedium", "bodySmall", "labelSmall", "displaySmall"), "color" ("primary", "secondary", "muted", "warning")
              * Icon: fields: "icon" (IconRequest), "size" ("xs", "sm", "md", "lg", "xl"), "color"
              * IconButton: fields: "icon" (IconRequest), "background"
              * Image: fields: "source" (BindingExpr)
              * Spacer: fields: "size" (BindingExpr)
              * Divider
              
            BindingExpr Options:
            - Binding to path: { "path": "/model/..." }
            - Format String: { "formatString": "Temp is {val}", "args": { "val": { "path": "/model/..." } } }
            - Literal String: { "literalString": "Static Text" }
            - Literal Number: { "literalNumber": 23.4 }
            - Literal Boolean: { "literalBoolean": true }
            
            IconRequest Options:
            - A reference string: { "ref": "ms:sunny" } or { "ref": "ms:rain" }
            - A path binding: { "path": "/model/.../iconRef", "fallbackRef": "ms:info" }
        """.trimIndent()

        return """
            $basePrompt
            
            ${adapter.getSystemPromptGuidance()}
        """.trimIndent()
    }
}
