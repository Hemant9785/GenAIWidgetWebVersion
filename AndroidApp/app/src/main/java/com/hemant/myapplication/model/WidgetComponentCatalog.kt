package com.hemant.myapplication.model

/**
 * Single source of truth for the components the Android renderer can safely
 * interpret. LLM prompts and parsed widget documents must both use this list.
 */
object WidgetComponentCatalog {
    val supportedComponents: Set<String> = linkedSetOf(
        "Column",
        "Row",
        "Text",
        "Icon",
        "IconButton",
        "InsightList",
        "EmptyState",
        "Spacer",
        "Divider",
    )

    fun promptCatalog(): String = """
        Global limits: at most 32 components, tree depth 5, 8 children per container, 8 InsightList items, and 4 text lines. Leaf components must have no children. Bindings must begin with `/model`.
        - Column: fields: `gap` (xs, sm, md, lg), `padding` (xs, sm, md, lg), `background` (surface, transparent), `cornerRadius` (sm, md, lg), `verticalAlign` (center, end, spaceBetween)
        - Row: fields: `gap`, `align` (center, end, spaceBetween)
        - Text: fields: `text` (BindingExpr), `style` (titleLarge, titleMedium, bodySmall, labelSmall, displaySmall), `color` (primary, secondary, muted, warning), optional `maxLines`
        - Icon: fields: `icon` (IconRequest), `size` (xs, sm, md, lg, xl), `color` (primary, secondary, muted, warning)
        - IconButton: fields: `icon` (IconRequest), `background` (transparent, default), `action`
        - InsightList: fields: `source` (BindingExpr.Path to an array), `presentation` (chips, list), `layout` (horizontal, vertical), `maxItems`, `columns`
        - EmptyState: fields: `title`, `message`, optional `action` and `actionLabel`
        - Spacer: fields: `size` (xs, sm, md, lg, xl)
        - Divider: no required fields
    """.trimIndent()
}
