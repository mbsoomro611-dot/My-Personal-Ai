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
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var transcriptText: TextView
    private lateinit var contactsListText: TextView
    private lateinit var prefs: SharedPreferences
    private val contacts = mutableListOf<Pair<String, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("voice_ai_prefs", MODE_PRIVATE)
        statusText = findViewById(R.id.statusText)
        transcriptText = findViewById(R.id.transcriptText)
        contactsListText = findViewById(R.id.contactsListText)

        findViewById<Button>(R.id.micButton).setOnClickListener { startListening() }
        findViewById<Button>(R.id.enableAccessibilityButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val nameInput = findViewById<EditText>(R.id.contactNameInput)
        val phoneInput = findViewById<EditText>(R.id.contactPhoneInput)
        findViewById<Button>(R.id.addContactButton).setOnClickListener {
            val name = nameInput.text.toString().trim()
            val phone = phoneInput.text.toString().trim()
            if (name.isNotEmpty() && phone.isNotEmpty()) {
                contacts.add(name to phone)
                saveContacts()
                nameInput.setText("")
                phoneInput.setText("")
                refreshContactsList()
                Toast.makeText(this, "Contact saved", Toast.LENGTH_SHORT).show()
            }
        }

        loadContacts()
        refreshContactsList()
        ensureAudioPermission()
        setupRecognizer()
    }

    private fun saveContacts() {
        val arr = JSONArray()
        for ((name, phone) in contacts) {
            arr.put(JSONObject().apply { put("name", name); put("phone", phone) })
        }
        prefs.edit().putString("contacts", arr.toString()).apply()
    }

    private fun loadContacts() {
        contacts.clear()
        val raw = prefs.getString("contacts", null) ?: return
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            contacts.add(obj.getString("name") to obj.getString("phone"))
        }
    }

    private fun refreshContactsList() {
        contactsListText.text = if (contacts.isEmpty()) {
            "Koi contact nahi hai abhi"
        } else {
            contacts.joinToString("\n") { "${it.first} — ${it.second}" }
        }
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

    private fun findContact(text: String): Pair<String, String>? {
        val lower = text.lowercase()
        return contacts.find { lower.contains(it.first.lowercase()) }
    }

    private fun handleTranscript(text: String) {
        val lower = text.lowercase()

        when {
            lower.contains("whatsapp") -> {
                val contact = findContact(text)
                if (contact == null) {
                    statusText.text = "Contact nahi mila, pehle neeche add karein"
                    return
                }
                var message = text
                for (trigger in listOf("bolo", "kaho", "likho", "message", "msg")) {
                    val idx = lower.indexOf(trigger)
                    if (idx != -1) {
                        message = text.substring(idx + trigger.length).trim()
                        break
                    }
                }
                if (message.isBlank()) message = text

                val json = JSONObject().apply {
                    put("action", "whatsapp_message")
                    put("contact_name", contact.first)
                    put("message", message)
                }
                AutomationAccessibilityService.queueCommand(json)
                statusText.text = "WhatsApp khol raha hoon: ${contact.first}"
                val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toast.makeText(this, "WhatsApp install nahi hai", Toast.LENGTH_SHORT).show()
                }
            }
            lower.contains("call") || lower.contains("phone") || lower.contains("ghanti") -> {
                val contact = findContact(text)
                if (contact == null) {
                    statusText.text = "Contact nahi mila, pehle neeche add karein"
                    return
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startActivity(Intent(Intent.ACTION_CALL).apply {
                        data = android.net.Uri.parse("tel:${contact.second}")
                    })
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 101)
                }
            }
            else -> {
                statusText.text = "Samajh nahi aaya. 'WhatsApp' ya 'Call' bolein contact ke naam ke saath"
            }
        }
    }
}
