package com.example.voiceai

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class AutomationAccessibilityService : AccessibilityService() {

    companion object {
        private var pending: JSONObject? = null
        fun queueCommand(json: JSONObject) { pending = json }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var step = 0
    private var lastPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val cmd = pending ?: return
        val pkg = event.packageName?.toString() ?: return

        if (pkg != lastPackage) {
            lastPackage = pkg
            step = 0
        }

        if (pkg == "com.whatsapp" && cmd.optString("action") == "whatsapp_message") {
            handler.postDelayed({ runWhatsappSteps(cmd) }, 400)
        }
    }

    private fun runWhatsappSteps(cmd: JSONObject) {
        val root = rootInActiveWindow ?: return
        val contact = cmd.optString("contact_name")
        val message = cmd.optString("message")

        when (step) {
            0 -> {
                val search = findNodeByDesc(root, "Search") ?: findNodeByDesc(root, "Talaash")
                if (search != null) {
                    search.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    step = 1
                }
            }
            1 -> {
                val field = findFirstEditable(root)
                if (field != null) {
                    setText(field, contact)
                    step = 2
                }
            }
            2 -> {
                val result = findFirstClickableResult(root, contact)
                if (result != null) {
                    result.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    step = 3
                }
            }
            3 -> {
                val field = findFirstEditable(root)
                if (field != null) {
                    setText(field, message)
                    step = 4
                }
            }
            4 -> {
                val send = findNodeByDesc(root, "Send")
                if (send != null) {
                    send.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    step = 5
                    pending = null
                }
            }
        }
    }

    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun findNodeByDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (root.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findNodeByDesc(child, desc)?.let { return it }
        }
        return null
    }

    private fun findFirstEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (root.isEditable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findFirstEditable(child)?.let { return it }
        }
        return null
    }

    private fun findFirstClickableResult(root: AccessibilityNodeInfo, nameHint: String): AccessibilityNodeInfo? {
        if (root.isClickable && root.text?.toString()?.contains(nameHint, ignoreCase = true) == true) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            findFirstClickableResult(child, nameHint)?.let { return it }
        }
        return null
    }

    override fun onInterrupt() {}
}
