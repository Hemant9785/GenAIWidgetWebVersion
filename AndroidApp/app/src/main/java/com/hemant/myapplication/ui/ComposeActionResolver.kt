package com.hemant.myapplication.ui

import com.hemant.myapplication.model.ActionEvent
import com.hemant.myapplication.model.ComponentValue

data class ComposeActionItem(
    val event: ActionEvent,
    val label: ComponentValue? = null,
    val icon: ComponentValue? = null,
)

class ComposeActionResolver {
    fun event(value: ComponentValue?): ActionEvent? {
        return when (value) {
            is ComponentValue.Action -> value.event
            is ComponentValue.Text -> value.value.takeIf { it.isNotBlank() }?.let { ActionEvent(it) }
            is ComponentValue.ObjectValue -> {
                val wrappedAction = value.values["action"]
                if (wrappedAction != null) {
                    return event(wrappedAction)
                }
                val event = value.values["event"] as? ComponentValue.ObjectValue ?: return null
                val name = (event.values["name"] as? ComponentValue.Text)?.value.orEmpty()
                if (name.isBlank()) null else ActionEvent(name)
            }
            else -> null
        }
    }

    fun events(value: ComponentValue?): List<ActionEvent> {
        if (value is ComponentValue.ListValue) {
            return value.values.mapNotNull { event(it) }
        }
        return event(value)?.let { listOf(it) }.orEmpty()
    }

    fun items(value: ComponentValue?): List<ComposeActionItem> {
        if (value is ComponentValue.ListValue) {
            return value.values.mapNotNull { item(it) }
        }
        return item(value)?.let { listOf(it) }.orEmpty()
    }

    private fun item(value: ComponentValue?): ComposeActionItem? {
        val event = event(value) ?: return null
        if (value !is ComponentValue.ObjectValue) {
            return ComposeActionItem(event = event, icon = defaultIcon(event.name))
        }
        return ComposeActionItem(
            event = event,
            label = value.values["label"] ?: value.values["title"] ?: value.values["text"],
            icon = value.values["icon"] ?: defaultIcon(event.name),
        )
    }

    private fun defaultIcon(actionName: String): ComponentValue? {
        val token = actionName.substringAfterLast('.').lowercase().replace("_", "")
        return when (token) {
            "call", "callvip", "opendialer" -> ComponentValue.Text("ms:call")
            "sms", "smsvip", "composesms" -> ComponentValue.Text("ms:sms")
            "contact", "opencontact" -> ComponentValue.Text("ms:person")
            "refresh" -> ComponentValue.Text("ms:refresh")
            "openconfig", "settings", "configure" -> ComponentValue.Text("ms:settings")
            else -> null
        }
    }
}
