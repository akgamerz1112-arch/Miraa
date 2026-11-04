package com.zenro.app.data

import com.zenro.app.voice.Errors
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** One cheap authenticated call to check the key. Key goes in a header, never logged. Call off the main thread. */
object KeyTester {
    fun run(key: String): String {
        val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build()
        val req = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?pageSize=1")
            .header("x-goog-api-key", key)
            .build()
        return try {
            client.newCall(req).execute().use { Errors.keyTest(it.code) }
        } catch (e: Exception) {
            Errors.keyTest(null)
        }
    }
}
