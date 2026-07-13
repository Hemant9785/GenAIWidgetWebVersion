package com.hemant.myapplication.ui

import com.hemant.myapplication.model.BindingExpr
import com.hemant.myapplication.model.ComponentValue
import org.json.JSONArray
import org.json.JSONObject

class ComposeBindingResolver(private val values: JSONObject?) {
    fun text(value: ComponentValue?): String {
        val resolved = value(value)
        return if (resolved == null || resolved === JSONObject.NULL) "" else resolved.toString()
    }

    fun number(value: ComponentValue?): Double {
        val resolved = value(value)
        return when (resolved) {
            is Number -> resolved.toDouble()
            is String -> resolved.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    fun boolean(value: ComponentValue?): Boolean = truthy(value(value))

    fun array(value: ComponentValue?): JSONArray? = value(value) as? JSONArray

    fun objectValue(value: ComponentValue?): JSONObject? = value(value) as? JSONObject

    fun value(value: ComponentValue?): Any? {
        return when (value) {
            null, ComponentValue.NullValue -> null
            is ComponentValue.Binding -> expr(value.expr)
            is ComponentValue.Text -> value.value
            is ComponentValue.Number -> value.value
            is ComponentValue.Flag -> value.value
            is ComponentValue.Icon -> value.request.ref ?: value.request.fallbackRef
            is ComponentValue.Action -> value.event.name
            is ComponentValue.ListValue -> JSONArray().also { out ->
                value.values.forEach { out.put(this.value(it)) }
            }
            is ComponentValue.ObjectValue -> JSONObject().also { out ->
                value.values.forEach { (key, item) -> out.put(key, this.value(item) ?: JSONObject.NULL) }
            }
        }
    }

    fun expr(expr: BindingExpr): Any? {
        return when (expr) {
            is BindingExpr.Path -> path(expr.pointer)
            is BindingExpr.LiteralString -> expr.value
            is BindingExpr.LiteralNumber -> expr.value
            is BindingExpr.LiteralBoolean -> expr.value
            BindingExpr.LiteralNull -> null
            is BindingExpr.FormatString -> {
                var out = expr.template
                expr.args.forEach { (key, arg) -> out = out.replace("{$key}", expr(arg)?.toString().orEmpty()) }
                out
            }
        }
    }

    fun visible(value: ComponentValue?): Boolean {
        if (value == null) return true
        if (value !is ComponentValue.ObjectValue) return truthy(value(value))
        val op = (value.values["op"] as? ComponentValue.Text)?.value.orEmpty()
        if (op.isBlank()) return truthy(value(value))
        val left = value(value.values["left"])
        val right = value(value.values["right"])
        return when (op) {
            "==" -> compare(left) == compare(right)
            "!=" -> compare(left) != compare(right)
            ">" -> numeric(left) > numeric(right)
            ">=" -> numeric(left) >= numeric(right)
            "<" -> numeric(left) < numeric(right)
            "<=" -> numeric(left) <= numeric(right)
            "notEmpty" -> !isEmpty(value(value.values["path"]) ?: left)
            "isEmpty" -> isEmpty(value(value.values["path"]) ?: left)
            else -> false
        }
    }

    fun path(path: String?): Any? {
        if (path.isNullOrBlank()) return null
        val trimmed = path.trim()
        if (trimmed == "/") return values
        val parts = if (trimmed.startsWith("/")) {
            trimmed.substring(1).split("/")
        } else {
            trimmed.split(".")
        }
        var current: Any? = values
        for (rawPart in parts) {
            val part = rawPart.replace("~1", "/").replace("~0", "~")
            current = when (current) {
                is JSONObject -> current.opt(part)
                is JSONArray -> if (part == "count") current.length() else part.toIntOrNull()?.let { current.opt(it) }
                else -> return null
            }
            if (current == null || current === JSONObject.NULL) return null
        }
        return current
    }

    private fun truthy(value: Any?): Boolean {
        return when (value) {
            null, JSONObject.NULL -> false
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotBlank()
            is JSONArray -> value.length() > 0
            else -> true
        }
    }

    private fun compare(value: Any?): String = if (value == null || value === JSONObject.NULL) "" else value.toString()

    private fun numeric(value: Any?): Double {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    private fun isEmpty(value: Any?): Boolean {
        return when (value) {
            null, JSONObject.NULL -> true
            is String -> value.isBlank()
            is JSONArray -> value.length() == 0
            is JSONObject -> value.length() == 0
            else -> false
        }
    }
}
