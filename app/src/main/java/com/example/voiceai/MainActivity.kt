package com.example.voiceai

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("voice_ai_prefs", MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)

        findViewById<Button>(R.id.micButton).setOnClickListener { startListening() }
        findViewById<Button>(R.id.enableAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val apiKeyInput = findViewById<android.widget.EditText>(R.id.apiKeyInput)
        apiKeyInput.setText(prefs.getString("api_key", ""))
        findViewById<Button>(R.id.saveKeyButton).setOnClickListener {
            prefs.edit().putString("api_key", apiKeyInput.text.toString().trim()).apply()
            Toast.makeText(this, "Key saved", Toast.LENGTH_SHORT).show()
        }

        ensureAudioPermission()
        setupRecognizer()
    }

    private fun ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun setupRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull().orEmpty()
                transcriptText.text = "\"$text\""
                if (text.isNotBlank()) handleTranscript(text)
            }

            override fun onError(error: Int) {
                statusText.text = "Sunai nahi diya, dobara try karein"
            }

            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "Sun raha hoon…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "Samajh raha hoon…" }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
        }
        speechRecognizer.startListening(intent)
    }

    private fun handleTranscript(text: String) {
        val apiKey = prefs.getString("api_key", null)
        if (apiKey.isNullOrBlank()) {
            Toast.makeText(this, "Pehle Settings mein Anthropic API key daalein", Toast.LENGTH_LONG).show()
            return
        }
        ClaudeApiClient.parseIntent(apiKey, text) { jsonOrNull ->
            runOnUiThread {
                if (jsonOrNull == null) {
                    statusText.text = "Samajh nahi paya, dobara boliye"
                    return@runOnUiThread
                }
                dispatchCommand(jsonOrNull)
            }
        }
    }

    private fun dispatchCommand(json: JSONObject) {
        val action = json.optString("action")
        statusText.text = "Kar raha hoon: $action"

        when (action) {
            "call" -> {
                val number = json.optString("phone_number")
                if (number.isNotBlank()) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        startActivity(Intent(Intent.ACTION_CALL).apply { data = android.net.Uri.parse("tel:$number") })
                    } else {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 101)
                    }
                }
            }
            "whatsapp_message", "open_app" -> {
                AutomationAccessibilityService.queueCommand(json)
                val pkg = if (action == "whatsapp_message") "com.whatsapp" else json.optString("package_name", "com.whatsapp")
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "App install nahi hai: $pkg", Toast.LENGTH_SHORT).show()
                }
            }
            else -> statusText.text = "Yeh command samajh nahi aayi"
        }
    }
}
