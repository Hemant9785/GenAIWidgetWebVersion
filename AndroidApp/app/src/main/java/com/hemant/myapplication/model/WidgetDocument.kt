package com.hemant.myapplication.model

data class WidgetDocument(
    val id: String,
    val schemaVersion: String,
    val title: String,
    val defaultSize: String,
    val surfaces: Map<String, Surface>,
    val states: Map<String, WidgetState> = emptyMap(),
    val previewMockData: org.json.JSONObject? = null,
)

data class WidgetState(
    val name: String,
    val screenId: String,
    val layouts: Map<String, String>,
)

data class Surface(
    val id: String,
    val catalogId: String,
    val rootComponentId: String,
    val components: Map<String, ComponentNode>,
) {
    fun root(): ComponentNode? = components[rootComponentId]
}

data class ComponentNode(
    val id: String,
    val component: String,
    val fields: Map<String, ComponentValue> = emptyMap(),
    val children: List<String> = emptyList(),
)

sealed interface ComponentValue {
    data class Binding(val expr: BindingExpr) : ComponentValue
    data class Text(val value: String) : ComponentValue
    data class Number(val value: Double) : ComponentValue
    data class Flag(val value: Boolean) : ComponentValue
    data class Icon(val request: IconRequest) : ComponentValue
    data class Action(val event: ActionEvent) : ComponentValue
    data class ListValue(val values: List<ComponentValue>) : ComponentValue
    data class ObjectValue(val values: Map<String, ComponentValue>) : ComponentValue
    data object NullValue : ComponentValue
}

sealed interface BindingExpr {
    data class Path(val pointer: String) : BindingExpr
    data class LiteralString(val value: String) : BindingExpr
    data class LiteralNumber(val value: Double) : BindingExpr
    data class LiteralBoolean(val value: Boolean) : BindingExpr
    data object LiteralNull : BindingExpr
    data class FormatString(val template: String, val args: Map<String, BindingExpr> = emptyMap()) : BindingExpr
}

data class IconRequest(
    val ref: String? = null,
    val binding: BindingExpr.Path? = null,
    val fallbackRef: String? = null,
)

data class ActionEvent(
    val name: String,
    val params: Map<String, ComponentValue> = emptyMap(),
)
