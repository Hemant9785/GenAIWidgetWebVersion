package com.hemant.myapplication.util

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

object HttpUtil {
    @Throws(Exception::class)
    fun get(urlStr: String, connectTimeoutMs: Int = 10000, readTimeoutMs: Int = 10000): String {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.setRequestProperty("User-Agent", "GenWidgetApp/1.0")
        connection.setRequestProperty("Accept", "application/json")
        
        val status = connection.responseCode
        val stream = if (status in 200..399) connection.inputStream else connection.errorStream
        val body = readBody(stream)
        connection.disconnect()
        
        if (status !in 200..299) {
            throw Exception("HTTP Get failed with status $status: $body")
        }
        return body
    }

    @Throws(Exception::class)
    fun post(urlStr: String, jsonPayload: String, apiKey: String, connectTimeoutMs: Int = 15000, readTimeoutMs: Int = 15000): String {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = connectTimeoutMs
        connection.readTimeout = readTimeoutMs
        connection.doOutput = true
        connection.setRequestProperty("User-Agent", "GenWidgetApp/1.0")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        
        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
            writer.write(jsonPayload)
            writer.flush()
        }
        
        val status = connection.responseCode
        val stream = if (status in 200..399) connection.inputStream else connection.errorStream
        val body = readBody(stream)
        connection.disconnect()
        
        if (status !in 200..299) {
            throw Exception("HTTP Post failed with status $status: $body")
        }
        return body
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        val builder = StringBuilder()
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                builder.append(line)
                line = reader.readLine()
            }
        }
        return builder.toString()
    }
}
