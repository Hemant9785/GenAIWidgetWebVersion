package com.hemant.myapplication.model

import org.json.JSONObject

class RuntimeSnapshot(
    private val stateKey: String?,
    private val sizeKey: String?,
    private val values: JSONObject?
) {
    fun stateKey(): String? = stateKey

    fun sizeKey(): String? = sizeKey

    fun values(): JSONObject? = values
}
