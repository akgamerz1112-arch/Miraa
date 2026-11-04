package com.zenro.app.chat

import com.zenro.app.voice.Errors
import com.zenro.app.voice.Role
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Pure request/response handling for the typed Chat tab (Gemini generateContent, text only). */
object GeminiTextProtocol {
    /** Editable in Settings; Google renames models often. */
    const val DEFAULT_MODEL = "gemini-flash-latest"
    const val MAX_TURNS = 20

    fun url(model: String): String {
        val m = model.removePrefix("models/")
        return "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent"
    }

    /** Merges back-to-back turns of the same role and keeps the last [MAX_TURNS]; the API wants user/model turns. */
    fun turns(history: List<Pair<Role, String>>): List<Pair<Role, String>> {
        val out = ArrayList<Pair<Role, String>>()
        for ((r, t) in history) {
            if (t.isBlank()) continue
            if (out.isNotEmpty() && out.last().first == r) {
                out[out.lastIndex] = r to (out.last().second + "\n" + t.trim())
            } else {
                out.add(r to t.trim())
            }
        }
        var trimmed = if (out.size > MAX_TURNS) out.takeLast(MAX_TURNS) else out
        while (trimmed.isNotEmpty() && trimmed.first().first != Role.USER) trimmed = trimmed.drop(1)
        return trimmed
    }

    fun buildRequest(system: String, history: List<Pair<Role, String>>): String {
        val contents = JSONArray()
        for ((r, t) in turns(history)) {
            contents.put(
                JSONObject()
                    .put("role", if (r == Role.USER) "user" else "model")
                    .put("parts", JSONArray().put(JSONObject().put("text", t)))
            )
        }
        return JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 1.0))
            .toString()
    }

    /** Reply text, or null when Gemini sent nothing usable (blocked or empty). Thinking parts are skipped. */
    fun parseReply(json: String): String? {
        val root = try { JSONObject(json) } catch (e: Exception) { return null }
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought")) continue
            sb.append(p.optString("text"))
        }
        return sb.toString().trim().ifEmpty { null }
    }
}

sealed interface ChatResult {
    class Ok(val text: String) : ChatResult
    class Fail(val message: String) : ChatResult
}

class TextChatClient {
    private val client = OkHttpClient.Builder()
        .callTimeout(40, TimeUnit.SECONDS)
        .build()

    /** Async; [onResult] is called on a background thread. The key goes in a header, never in the URL. */
    fun send(
        apiKey: String,
        model: String,
        system: String,
        history: List<Pair<Role, String>>,
        onResult: (ChatResult) -> Unit,
    ) {
        val body = GeminiTextProtocol.buildRequest(system, history)
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url(GeminiTextProtocol.url(model))
            .header("x-goog-api-key", apiKey)
            .post(body)
            .build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult(ChatResult.Fail(Errors.network()))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val text = try { r.body?.string().orEmpty() } catch (e: Exception) { "" }
                    if (!r.isSuccessful) {
                        onResult(ChatResult.Fail(Errors.http(r.code)))
                        return
                    }
                    val reply = GeminiTextProtocol.parseReply(text)
                    onResult(
                        if (reply != null) ChatResult.Ok(reply)
                        else ChatResult.Fail("Jawab khaali aaya. Dobara bhejo ya Settings mein chat model badlo.")
                    )
                }
            }
        })
    }
}
