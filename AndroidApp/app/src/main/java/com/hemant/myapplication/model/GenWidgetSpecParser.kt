package com.hemant.myapplication.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object GenWidgetSpecParser {
    @Throws(Exception::class)
    fun parse(jsonSpec: String): WidgetDocument {
        val root = try {
            JSONObject(jsonSpec)
        } catch (e: JSONException) {
            throw Exception("JSONSpec is not valid JSON.", e)
        }
        val spec = GenWidgetSpec(root)
        return GenWidgetDocumentMapper.fromSpec(spec)
    }
}

class GenWidgetSpec(private val root: JSONObject) {
    fun root(): JSONObject = root
    fun schemaVersion(): String = root.optString("schemaVersion")
    fun widgetId(): String = root.optString("widgetId")
    fun displayName(): String = root.optJSONObject("metadata")?.optString("title") ?: widgetId()
    fun defaultSize(): String = root.optJSONObject("supportedSizes")?.optString("default", "4x3") ?: "4x3"
    
    fun stateDefinitions(): JSONObject? {
        return root.optJSONObject("stateMachine")?.optJSONObject("states")
    }
    
    fun screens(): JSONObject? = root.optJSONObject("screens")
    fun a2uiSurfaces(): JSONObject? = root.optJSONObject("a2uiSurfaces")
}

object GenWidgetDocumentMapper {
    fun fromSpec(spec: GenWidgetSpec): WidgetDocument {
        val metadata = spec.root().optJSONObject("metadata")
        val preview = spec.root().optJSONObject("preview")
        val mockData = preview?.optJSONObject("mockData")
        return WidgetDocument(
            id = spec.widgetId(),
            schemaVersion = spec.schemaVersion(),
            title = metadata?.optString("title", spec.displayName()) ?: spec.displayName(),
            defaultSize = spec.defaultSize(),
            surfaces = surfaces(spec),
            states = states(spec),
            previewMockData = mockData,
        )
    }

    private fun states(spec: GenWidgetSpec): Map<String, WidgetState> {
        val states = linkedMapOf<String, WidgetState>()
        val definitions = spec.stateDefinitions() ?: return states
        val names = definitions.keys()
        while (names.hasNext()) {
            val name = names.next()
            val state = definitions.optJSONObject(name) ?: continue
            val screenId = state.optString("screen")
            val layouts = linkedMapOf<String, String>()
            val screenLayouts = spec.screens()?.optJSONObject(screenId)?.optJSONObject("layouts")
            val layoutNames = screenLayouts?.keys()
            if (layoutNames != null) {
                while (layoutNames.hasNext()) {
                    val size = layoutNames.next()
                    layouts[size] = screenLayouts.optString(size)
                }
            }
            states[name] = WidgetState(name, screenId, layouts)
        }
        return states
    }

    private fun surfaces(spec: GenWidgetSpec): Map<String, Surface> {
        val result = linkedMapOf<String, Surface>()
        val a2uiSurfaces = spec.a2uiSurfaces() ?: return result
        val surfaceIds = a2uiSurfaces.keys()
        while (surfaceIds.hasNext()) {
            val wrapperId = surfaceIds.next()
            val createSurface = a2uiSurfaces.optJSONObject(wrapperId)?.optJSONObject("createSurface") ?: continue
            val surfaceId = createSurface.optString("surfaceId", wrapperId)
            val components = components(createSurface.optJSONArray("components"))
            result[surfaceId] = Surface(
                id = surfaceId,
                catalogId = createSurface.optString("catalogId"),
                rootComponentId = createSurface.optString("root"),
                components = components,
            )
        }
        return result
    }

    private fun components(rawComponents: JSONArray?): Map<String, ComponentNode> {
        val components = linkedMapOf<String, ComponentNode>()
        if (rawComponents == null) return components
        for (i in 0 until rawComponents.length()) {
            val raw = rawComponents.optJSONObject(i) ?: continue
            val id = raw.optString("id")
            components[id] = ComponentNode(
                id = id,
                component = raw.optString("component"),
                fields = componentFields(raw),
                children = children(raw.optJSONArray("children")),
            )
        }
        return components
    }

    private fun componentFields(component: JSONObject): Map<String, ComponentValue> {
        val fields = linkedMapOf<String, ComponentValue>()
        
        // Extract properties from the nested "fields" object if it exists
        val nestedFields = component.optJSONObject("fields")
        if (nestedFields != null) {
            val keys = nestedFields.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                fields[key] = componentValue(nestedFields.opt(key))
            }
        }
        
        // Also extract any top-level properties (except system keys)
        val keys = component.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id" || key == "component" || key == "children" || key == "fields") continue
            fields[key] = componentValue(component.opt(key))
        }
        return fields
    }

    private fun children(rawChildren: JSONArray?): List<String> {
        if (rawChildren == null) return emptyList()
        return List(rawChildren.length()) { index -> rawChildren.optString(index) }
            .filter { it.isNotEmpty() }
    }

    private fun componentValue(value: Any?): ComponentValue {
        return when (value) {
            null, JSONObject.NULL -> ComponentValue.NullValue
            is JSONObject -> objectValue(value)
            is JSONArray -> ComponentValue.ListValue(List(value.length()) { componentValue(value.opt(it)) })
            is Boolean -> ComponentValue.Flag(value)
            is Number -> ComponentValue.Number(value.toDouble())
            else -> ComponentValue.Text(value.toString())
        }
    }

    private fun objectValue(value: JSONObject): ComponentValue {
        iconRequest(value)?.let { return ComponentValue.Icon(it) }
        bindingExpr(value)?.let { return ComponentValue.Binding(it) }
        actionEvent(value)?.let { return ComponentValue.Action(it) }

        val fields = linkedMapOf<String, ComponentValue>()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            fields[key] = componentValue(value.opt(key))
        }
        return ComponentValue.ObjectValue(fields)
    }

    private fun bindingExpr(value: JSONObject): BindingExpr? {
        if (value.has("path")) return BindingExpr.Path(value.optString("path"))
        if (value.has("literalString")) return BindingExpr.LiteralString(value.optString("literalString"))
        if (value.has("literalNumber")) return BindingExpr.LiteralNumber(value.optDouble("literalNumber"))
        if (value.has("literalBoolean")) return BindingExpr.LiteralBoolean(value.optBoolean("literalBoolean"))
        if (value.has("literalNull")) return BindingExpr.LiteralNull
        if (value.has("formatString")) {
            return BindingExpr.FormatString(
                template = value.optString("formatString"),
                args = bindingArgs(value.optJSONObject("args")),
            )
        }
        return null
    }

    private fun bindingArgs(args: JSONObject?): Map<String, BindingExpr> {
        if (args == null) return emptyMap()
        val result = linkedMapOf<String, BindingExpr>()
        val keys = args.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val argObj = args.optJSONObject(key)
            val expr = if (argObj != null) bindingExpr(argObj) else null
            if (expr != null) result[key] = expr
        }
        return result
    }

    private fun actionEvent(value: JSONObject): ActionEvent? {
        val event = value.optJSONObject("event") ?: return null
        val name = event.optString("name")
        if (name.isEmpty()) return null
        val params = linkedMapOf<String, ComponentValue>()
        val rawParams = event.optJSONObject("params")
        val keys = rawParams?.keys()
        if (keys != null) {
            while (keys.hasNext()) {
                val key = keys.next()
                params[key] = componentValue(rawParams.opt(key))
            }
        }
        return ActionEvent(name, params)
    }

    private fun iconRequest(value: JSONObject): IconRequest? {
        if (!value.has("ref") && !value.has("fallbackRef")) return null
        val ref = value.optString("ref", "")
        val fallbackRef = value.optString("fallbackRef", "")
        val path = value.optString("path", "")
        if (ref.isEmpty() && fallbackRef.isEmpty() && path.isEmpty()) return null
        return IconRequest(
            ref = ref.ifEmpty { null },
            binding = path.ifEmpty { null }?.let { BindingExpr.Path(it) },
            fallbackRef = fallbackRef.ifEmpty { null },
        )
    }
}
