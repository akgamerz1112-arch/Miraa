package com.zenro.app.voice

/** Plain Hinglish messages for the failures a user can actually fix. Pure Kotlin. */
object Errors {
    fun closed(code: Int, reason: String): String {
        val r = reason.lowercase()
        // The server's own words are the fastest way to find the real cause, so show a short piece of them.
        val why = reason.trim().replace(Regex("\\s+"), " ").take(180).let { if (it.isEmpty()) "" else " Server ne kaha: $it" }
        return when {
            r.contains("quota") || r.contains("exhausted") || r.contains("rate limit") ->
                "Free quota khatam ho gaya lagta hai. Thodi der baad dobara try karo."
            code == 1007 || code == 1008 || r.contains("api key") || r.contains("permission") ->
                "Key ya model galat lagta hai ($code). Settings mein key aur model check karo.$why"
            else -> "Connection toot gaya ($code). Orb tap karke dobara try karo.$why"
        }
    }

    fun http(code: Int): String = when (code) {
        400, 401, 403 -> "Key galat hai ya is key ko permission nahi ($code). Settings mein check karo."
        404 -> "Model ka naam galat lagta hai (404). Settings mein model badlo."
        429 -> "Free quota khatam ho gaya (429). Thodi der baad try karo."
        in 500..599 -> "Google ka server abhi dikkat mein hai ($code). Thodi der baad try karo."
        else -> "Server error ($code)."
    }

    fun network(): String = "Internet nahi mil raha. Wi-Fi ya mobile data check karo."

    fun mic(): String = "Mic chalu nahi hua. Shayad koi aur app mic use kar raha hai."

    fun keyTest(code: Int?): String = when (code) {
        200 -> "Key sahi hai. (Live model ka asli test baat shuru karne pe hoga.)"
        null -> network()
        else -> http(code)
    }
}
