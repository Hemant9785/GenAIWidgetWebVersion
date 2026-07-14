package com.hemant.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class WidgetSpecValidatorTest {
    @Test
    fun acceptsBoundedReachableWidgetTree() {
        val document = documentOf(
            mapOf(
                "root" to ComponentNode("root", "Column", children = listOf("title")),
                "title" to ComponentNode(
                    id = "title",
                    component = "Text",
                    fields = mapOf(
                        "text" to ComponentValue.Binding(BindingExpr.Path("/model/weather/temperatureText")),
                        "style" to ComponentValue.Text("titleLarge"),
                        "maxLines" to ComponentValue.Number(2.0),
                    ),
                ),
            ),
        )

        WidgetSpecValidator.validate(document)
        assertEquals("widget", document.id)
    }

    @Test
    fun rejectsTreeDeeperThanFiveLevels() {
        val components = (0..5).associate { index ->
            val id = "node$index"
            id to ComponentNode(
                id = id,
                component = "Column",
                children = if (index == 5) emptyList() else listOf("node${index + 1}"),
            )
        }

        assertRejected { WidgetSpecValidator.validate(documentOf(components)) }
    }

    @Test
    fun rejectsUnsafeTextLineCount() {
        val document = documentOf(
            mapOf(
                "root" to ComponentNode("root", "Column", children = listOf("title")),
                "title" to ComponentNode(
                    id = "title",
                    component = "Text",
                    fields = mapOf(
                        "text" to ComponentValue.Binding(BindingExpr.Path("/model/summary/title")),
                        "maxLines" to ComponentValue.Number(5.0),
                    ),
                ),
            ),
        )

        assertRejected { WidgetSpecValidator.validate(document) }
    }

    private fun documentOf(components: Map<String, ComponentNode>): WidgetDocument = WidgetDocument(
        id = "widget",
        schemaVersion = "0.1",
        title = "Widget",
        defaultSize = "4x3",
        surfaces = mapOf(
            "surface.ready.4x3" to Surface(
                id = "surface.ready.4x3",
                catalogId = "genwidget://catalog/android-widget-v1",
                rootComponentId = "root",
                components = components,
            ),
        ),
    )

    private fun assertRejected(block: () -> Unit) {
        try {
            block()
            fail("Expected validation to reject the document")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
