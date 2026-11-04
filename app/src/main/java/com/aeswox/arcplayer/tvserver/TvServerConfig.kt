package com.aeswox.arcplayer.tvserver

import android.content.Context
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom

/**
 * Everything the LAN server needs to know about itself.
 *
 * Off by default. This is a door into the whole film shelf, and a door nobody asked for is not a feature
 * — the viewer opens it from Settings, and only then does anything bind a port.
 */
data class TvServerConfig(
    val enabled: Boolean,
    val port: Int,
    val token: String,
)

object TvServerConfigStore {

    const val DEFAULT_PORT = 8080

    private const val PREFS = "arc_player_prefs"
    private const val KEY_ENABLED = "tv_server_enabled"
    private const val KEY_PORT = "tv_server_port"
    private const val KEY_TOKEN = "tv_server_token"
    private const val KEY_DEMO_FILE = "tv_server_demo_file"
    private const val HEX = "0123456789abcdef"

    /**
     * The stored picture, with a token minted on the spot if this install has never had one.
     *
     * [enabled] is read as false when nothing is stored: the token write below is the only side effect
     * this has, and it should not happen on a phone whose owner never opened the door.
     */
    fun load(context: Context): TvServerConfig {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var token = prefs.getString(KEY_TOKEN, null).orEmpty()
        if (token.isBlank()) {
            token = newToken()
            prefs.edit().putString(KEY_TOKEN, token).apply()
        }
        return TvServerConfig(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            port = prefs.getInt(KEY_PORT, DEFAULT_PORT),
            token = token,
        )
    }

    fun save(context: Context, config: TvServerConfig) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, config.enabled)
            .putInt(KEY_PORT, config.port)
            .putString(KEY_TOKEN, config.token)
            .apply()
    }

    /** Drops the token a TV is holding, so its next request is a 401 and it goes back to pairing. */
    fun rotateToken(context: Context): String {
        val token = newToken()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .apply()
        return token
    }

    /** Debug only: one film named by path, served as `/stream/demo` so Range can be proven by hand. */
    fun demoFile(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DEMO_FILE, null)
            ?.takeIf { it.isNotBlank() }

    fun setDemoFile(context: Context, path: String?) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply { if (path.isNullOrBlank()) remove(KEY_DEMO_FILE) else putString(KEY_DEMO_FILE, path) }
            .apply()
    }

    private fun newToken(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val bits = byte.toInt() and 0xFF
                append(HEX[bits shr 4])
                append(HEX[bits and 0x0F])
            }
        }
    }
}

/**
 * The address a TV types in, which has to be the one that TV can actually reach.
 *
 * The interface list rather than the Wi-Fi manager: a peer on the network answers to what the phone's own
 * stack reports, and an address the system is happy with can still be one nothing off the phone sees. So
 * the interfaces a TV could be sharing a network with are ranked, and anything down, looping, or holding
 * something other than a private IPv4 is not offered at all — silence beats a wrong address, because a
 * wrong one looks like a broken server.
 */
object TvAddress {

    fun lanHost(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return null
        var best: String? = null
        var bestRank = Int.MAX_VALUE

        for (iface in interfaces.toList()) {
            // Both of these can throw for an interface that has no business being asked.
            val host = runCatching {
                if (!iface.isUp || iface.isLoopback) return@runCatching null
                iface.inetAddresses.toList()
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { it.isSiteLocalAddress }
                    ?.hostAddress
            }.getOrNull() ?: continue

            val rank = when {
                iface.name.startsWith("wlan") -> 0
                iface.name.startsWith("eth") -> 1
                else -> 2
            }
            if (rank < bestRank) {
                bestRank = rank
                best = host
            }
        }
        return best
    }
}
