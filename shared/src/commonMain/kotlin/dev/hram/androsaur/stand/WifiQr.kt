package dev.hram.androsaur.stand

/** Credentials stay in memory; default object rendering must never expose the password. */
class WifiQr private constructor(val ssid: String, val password: String) {
    override fun toString() = "WifiQr(<redacted>)"

    companion object {
        /** Standard WIFI: payload, including backslash-escaped delimiters used by router labels. */
        fun parse(raw: String): WifiQr {
            require(raw.startsWith("WIFI:") && raw.length <= 512) { "Это не QR-код Wi-Fi" }
            val fields = mutableMapOf<String, String>()
            val part = StringBuilder()
            var escaped = false
            fun field() {
                val value = part.toString()
                part.clear()
                if (value.isEmpty()) return
                val separator = value.indexOf(':')
                require(separator > 0) { "Повреждён QR-код Wi-Fi" }
                val key = value.substring(0, separator)
                require(key !in fields) { "Повреждён QR-код Wi-Fi" }
                fields[key] = value.substring(separator + 1)
            }
            for (char in raw.substring(5)) {
                when {
                    escaped -> { part.append(char); escaped = false }
                    char == '\\' -> escaped = true
                    char == ';' -> field()
                    else -> part.append(char)
                }
            }
            require(!escaped) { "Повреждён QR-код Wi-Fi" }
            field()
            val ssid = fields["S"].orEmpty()
            require(ssid.isNotEmpty() && ssid.encodeToByteArray().size <= 32 && '\u0000' !in ssid) { "Некорректное имя сети Wi-Fi" }
            val auth = fields["T"].orEmpty().uppercase()
            require(auth in setOf("", "NOPASS", "WPA", "WPA2", "WPA/WPA2")) { "Этот тип защиты Wi-Fi не поддерживается подставкой" }
            val password = if (auth == "NOPASS" || auth.isEmpty()) "" else fields["P"].orEmpty()
            require('\u0000' !in password && (auth in setOf("", "NOPASS") || password.encodeToByteArray().size in 8..63)) { "Некорректный пароль Wi-Fi" }
            return WifiQr(ssid, password)
        }
    }
}
