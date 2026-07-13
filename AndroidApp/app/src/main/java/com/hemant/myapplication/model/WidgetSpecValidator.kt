package com.hemant.myapplication.model

/** Reject malformed or unsupported model output before the Compose renderer sees it. */
object WidgetSpecValidator {
    private const val MAX_COMPONENTS_PER_SURFACE = 64
    private const val MAX_TREE_DEPTH = 12

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
                require(component.id.isNotBlank()) { "Component IDs must not be blank" }
                require(component.component in WidgetComponentCatalog.supportedComponents) {
                    "Unsupported component '${component.component}'"
                }
                component.children.forEach { childId ->
                    require(surface.components.containsKey(childId)) {
                        "Component '${component.id}' references missing child '$childId'"
                    }
                }
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
}
