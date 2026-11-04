package com.mira.app.control

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.view.KeyEvent
import androidx.core.content.ContextCompat

/** Phone actions that use normal Android intents / AudioManager. Each returns null on success or a Hinglish error. */
object PhoneActions {

    private fun launch(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    fun youtubeSearch(ctx: Context, query: String): String? {
        if (query.isBlank()) return "Kya search karna hai, batao."
        val app = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube").putExtra("query", query)
        if (launch(ctx, app)) return null
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(LinkMath.youtubeSearchUrl(query)))
        return if (launch(ctx, web)) null else "YouTube khul nahi paya."
    }

    /** Either an exact [percent] (0-100) or a step [direction] (up/down) for the media volume. */
    fun setVolume(ctx: Context, percent: Int?, direction: String): String? {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        when {
            percent != null -> am.setStreamVolume(AudioManager.STREAM_MUSIC, LinkMath.volumeIndex(percent, max), 0)
            direction.equals("up", true) -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
            direction.equals("down", true) -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
            else -> return "percent (0-100) ya direction up/down do."
        }
        return null
    }

    /** play_pause / next / previous for whatever media app is playing. */
    fun media(ctx: Context, action: String): String? {
        val code = when (action.trim().lowercase()) {
            "play_pause", "play", "pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return "action play_pause, next ya previous hona chahiye."
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return null
    }

    /** Opens the WhatsApp chat with the text already typed. It does NOT press Send. */
    fun whatsapp(ctx: Context, to: String, text: String): String? {
        val number: String = if (LinkMath.looksLikeNumber(to)) {
            LinkMath.normalizeNumber(to) ?: return "Number chhota hai."
        } else {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return "Contacts ki permission nahi hai. User se number poochho ya Settings mein Contacts permission do."
            }
            val found = lookupNumber(ctx, to.trim()) ?: return "'$to' naam ka contact nahi mila."
            LinkMath.normalizeNumber(found) ?: return "Contact ka number sahi nahi hai."
        }
        val uri = Uri.parse(LinkMath.whatsappUrl(number, text))
        val wa = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")
        val biz = Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp.w4b")
        return if (launch(ctx, wa) || launch(ctx, biz)) null else "WhatsApp khul nahi paya."
    }

    private fun lookupNumber(ctx: Context, name: String): String? {
        val c = ctx.contentResolver.query(
            Phone.CONTENT_URI, arrayOf(Phone.NUMBER),
            Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null
        ) ?: return null
        c.use { return if (it.moveToFirst()) it.getString(0) else null }
    }
}
