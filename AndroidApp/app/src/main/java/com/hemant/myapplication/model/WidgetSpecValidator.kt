package com.hemant.myapplication.model

/** Reject malformed, oversized, or unsupported model output before Compose sees it. */
object WidgetSpecValidator {
    private const val MAX_COMPONENTS_PER_SURFACE = 32
    private const val MAX_TREE_DEPTH = 5
    private const val MAX_CHILDREN_PER_COMPONENT = 8
    private const val MAX_LITERAL_TEXT_LENGTH = 280
    private const val MAX_LIST_ITEMS = 8
    private const val MAX_OBJECT_FIELDS = 16
    private const val MAX_VALUE_DEPTH = 4

    private val allowedFields = mapOf(
        "Column" to setOf("gap", "padding", "background", "cornerRadius", "verticalAlign", "align", "visibleWhen", "action"),
        "Row" to setOf("gap", "align", "padding", "background", "cornerRadius", "visibleWhen", "action"),
        "Text" to setOf("text", "label", "style", "color", "maxLines", "visibleWhen", "action"),
        "Icon" to setOf("icon", "size", "color", "visibleWhen", "action"),
        "IconButton" to setOf("icon", "background", "visibleWhen", "action"),
        "InsightList" to setOf("source", "items", "presentation", "layout", "maxItems", "columns", "visibleWhen"),
        "EmptyState" to setOf("title", "message", "action", "actionLabel", "visibleWhen"),
        "Spacer" to setOf("size", "visibleWhen"),
        "Divider" to setOf("visibleWhen"),
    )
    private val containerComponents = setOf("Column", "Row")

    fun validate(document: WidgetDocument) {
        require(document.id.isNotBlank()) { "Widget document requires widgetId" }
        require(document.schemaVersion == "0.1") { "Unsupported schema version '${document.schemaVersion}'" }
        require(document.surfaces.isNotEmpty()) { "Widget document requires at least one surface" }

        document.surfaces.values.forEach { surface ->
            require(surface.components.size <= MAX_COMPONENTS_PER_SURFACE) {
                "Surface '${surface.id}' exceeds $MAX_COMPONENTS_PER_SURFACE components"
            }
            require(surface.components.containsKey(surface.rootComponentId)) {
                "Surface '${surface.id}' has no root component '${surface.rootComponentId}'"
            }

            surface.components.values.forEach { component ->
                validateComponent(surface, component)
            }

            val visiting = HashSet<String>()
            val reachable = HashSet<String>()
            fun visit(componentId: String, depth: Int) {
                require(depth <= MAX_TREE_DEPTH) {
                    "Surface '${surface.id}' exceeds max tree depth $MAX_TREE_DEPTH"
                }
                require(visiting.add(componentId)) {
                    "Surface '${surface.id}' contains a component cycle at '$componentId'"
                }
                reachable.add(componentId)
                surface.components.getValue(componentId).children.forEach { childId ->
                    visit(childId, depth + 1)
                }
                visiting.remove(componentId)
            }

            visit(surface.rootComponentId, depth = 1)
            require(reachable.size == surface.components.size) {
                "Surface '${surface.id}' contains components not reachable from the root"
            }
        }
    }

    private fun validateComponent(surface: Surface, component: ComponentNode) {
        require(component.id.isNotBlank()) { "Component IDs must not be blank" }
        require(component.component in WidgetComponentCatalog.supportedComponents) {
            "Unsupported component '${component.component}'"
        }
        require(component.children.size <= MAX_CHILDREN_PER_COMPONENT) {
            "Component '${component.id}' exceeds $MAX_CHILDREN_PER_COMPONENT children"
        }
        require(component.component in containerComponents || component.children.isEmpty()) {
            "Leaf component '${component.id}' must not have children"
        }
        component.children.forEach { childId ->
            require(surface.components.containsKey(childId)) {
                "Component '${component.id}' references missing child '$childId'"
            }
        }

        val allowed = allowedFields.getValue(component.component)
        component.fields.keys.forEach { field ->
            require(field in allowed) {
                "Component '${component.id}' does not support field '$field'"
            }
        }
        component.fields.forEach { (field, value) -> validateValue(value, component.id, field, 1) }

        validateToken(component, "gap", setOf("xs", "sm", "md", "lg"))
        validateToken(component, "padding", setOf("xs", "sm", "md", "lg"))
        validateToken(
            component,
            "background",
            if (component.component == "IconButton") setOf("transparent", "default") else setOf("surface", "transparent"),
        )
        validateToken(component, "cornerRadius", setOf("sm", "md", "lg"))
        validateToken(component, "verticalAlign", setOf("center", "end", "spaceBetween"))
        validateToken(component, "align", setOf("center", "end", "spaceBetween"))
        validateToken(component, "style", setOf("displaySmall", "titleLarge", "titleMedium", "bodySmall", "labelSmall"))
        validateToken(component, "color", setOf("primary", "secondary", "muted", "warning", "inverse"))
        validateToken(component, "size", setOf("xs", "sm", "md", "lg", "xl"))
        validateToken(component, "presentation", setOf("chips", "list"))
        validateToken(component, "layout", setOf("horizontal", "vertical"))

        validateBoundedNumber(component, "maxLines", 1, 4)
        validateBoundedNumber(component, "maxItems", 1, MAX_LIST_ITEMS)
        validateBoundedNumber(component, "columns", 1, 4)
    }

    private fun validateToken(component: ComponentNode, field: String, allowed: Set<String>) {
        val value = component.fields[field] ?: return
        val token = (value as? ComponentValue.Text)?.value
            ?: throw IllegalArgumentException("Field '$field' on '${component.id}' must be a literal token")
        require(token in allowed) { "Unsupported value '$token' for '$field' on '${component.id}'" }
    }

    private fun validateBoundedNumber(component: ComponentNode, field: String, min: Int, max: Int) {
        val value = component.fields[field] ?: return
        val number = (value as? ComponentValue.Number)?.value
            ?: throw IllegalArgumentException("Field '$field' on '${component.id}' must be numeric")
        require(number.isFinite() && number == number.toInt().toDouble() && number.toInt() in min..max) {
            "Field '$field' on '${component.id}' must be an integer between $min and $max"
        }
    }

    private fun validateValue(value: ComponentValue, componentId: String, field: String, depth: Int) {
        require(depth <= MAX_VALUE_DEPTH) { "Field '$field' on '$componentId' is nested too deeply" }
        when (value) {
            is ComponentValue.Text -> require(value.value.length <= MAX_LITERAL_TEXT_LENGTH) {
                "Literal text in '$field' on '$componentId' is too long"
            }
            is ComponentValue.Binding -> validateExpression(value.expr, componentId, field, depth)
            is ComponentValue.Icon -> value.request.binding?.let { validateExpression(it, componentId, field, depth) }
            is ComponentValue.Action -> {
                require(value.event.name.length <= 80) { "Action name on '$componentId' is too long" }
                require(value.event.params.size <= MAX_OBJECT_FIELDS) { "Action on '$componentId' has too many parameters" }
                value.event.params.forEach { (name, parameter) -> validateValue(parameter, componentId, "$field.$name", depth + 1) }
            }
            is ComponentValue.ListValue -> {
                require(value.values.size <= MAX_LIST_ITEMS) { "List '$field' on '$componentId' has too many items" }
                value.values.forEach { item -> validateValue(item, componentId, field, depth + 1) }
            }
            is ComponentValue.ObjectValue -> {
                require(value.values.size <= MAX_OBJECT_FIELDS) { "Object '$field' on '$componentId' has too many fields" }
                value.values.forEach { (name, child) -> validateValue(child, componentId, "$field.$name", depth + 1) }
            }
            else -> Unit
        }
    }

    private fun validateExpression(expression: BindingExpr, componentId: String, field: String, depth: Int) {
        when (expression) {
            is BindingExpr.Path -> require(expression.pointer == "/model" || expression.pointer.startsWith("/model/")) {
                "Binding '$field' on '$componentId' must target /model"
            }
            is BindingExpr.LiteralString -> require(expression.value.length <= MAX_LITERAL_TEXT_LENGTH) {
                "Literal binding '$field' on '$componentId' is too long"
            }
            is BindingExpr.FormatString -> {
                require(expression.template.length <= MAX_LITERAL_TEXT_LENGTH) {
                    "Format string '$field' on '$componentId' is too long"
                }
                require(expression.args.size <= MAX_OBJECT_FIELDS) {
                    "Format string '$field' on '$componentId' has too many arguments"
                }
                expression.args.values.forEach { argument -> validateExpression(argument, componentId, field, depth + 1) }
            }
            else -> Unit
        }
    }
}
