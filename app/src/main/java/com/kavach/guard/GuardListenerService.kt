package com.kavach.guard

import android.app.Notification
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Reads the text of incoming notifications (SMS, WhatsApp, Telegram, UPI apps, calls)
 * once the user grants "Notification access". No SMS permission needed.
 * Text is analysed on the phone and never uploaded.
 */
class GuardListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "Kavach listener connected")
        VoiceAlert.warmUp(this)
        Guard.ensureChannel(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

            val isCall = n.category == Notification.CATEGORY_CALL
            // Ongoing notifications (music, downloads, navigation) are noise, except incoming calls.
            if (sbn.isOngoing && !isCall) return

            val extras = n.extras ?: return
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = collectText(extras)
            if (title.isBlank() && text.isBlank()) return

            Guard.handle(
                this,
                Guard.Incoming(
                    pkg = sbn.packageName,
                    appLabel = appLabel(sbn.packageName),
                    title = title,
                    text = text,
                    isCall = isCall,
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect notification", e)
        }
    }

    private fun collectText(extras: Bundle): String {
        val parts = LinkedHashSet<String>()
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts += it.toString() }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { parts += it.toString() }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.takeLast(3)?.forEach { parts += it.toString() }
        // MessagingStyle (WhatsApp, Telegram, Google Messages): last few messages
        @Suppress("DEPRECATION")
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.takeLast(3)?.forEach { p ->
            (p as? Bundle)?.getCharSequence("text")?.let { parts += it.toString() }
        }
        return parts.joinToString(" ").take(1000)
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    companion object {
        private const val TAG = "KavachListener"
    }
}
