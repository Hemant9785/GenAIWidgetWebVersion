package com.hemant.myapplication.pipeline

import org.json.JSONArray

data class VariablePlan(
    val status: String,
    val variables: JSONArray,
    val assets: JSONArray
)
