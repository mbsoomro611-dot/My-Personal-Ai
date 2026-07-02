package com.example.voiceai

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ClaudeApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val SYSTEM_PROMPT = """
You convert a spoken command (Roman Urdu, Hindi, or English) into ONE JSON object.
Return ONLY raw JSON, no markdown, no explanation.

Schema:
{
  "action": "whatsapp_message" | "call" | "open_app" | "unknown",
  "contact_name": string,
  "phone_number": string,
  "message": string,
  "package_name": string
}
Leave fields empty ("") if not applicable. Never invent a phone number if none was spoken.
"""

    fun parseIntent(apiKey: String, transcript: String, callback: (JSONObject?) -> Unit) {
        val body = JSONObject().apply {
            put("model", "claude-sonnet-4-6")
            put("max_tokens", 300)
            put("system", SYSTEM_PROMPT.trim())
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", transcript)
            }))
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(RequestBody.create(MediaType.parse("application/json"), body.toString()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Handler(Looper.getMainLooper()).post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val raw = response.body()?.string()
                val parsed = try {
                    val root = JSONObject(raw ?: "")
                    val text = root.getJSONArray("content").getJSONObject(0).getString("text")
                    JSONObject(text.trim())
                } catch (e: Exception) {
                    null
                }
                Handler(Looper.getMainLooper()).post { callback(parsed) }
            }
        })
    }
}
