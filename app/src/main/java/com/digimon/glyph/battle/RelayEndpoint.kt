package com.digimon.glyph.battle

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** Secure relay URL normalization, independent of Android's URI implementation. */
object RelayEndpoint {
    data class WebSocket(val url: String, val room: String)

    fun websocket(raw: String, fallbackRoom: String): WebSocket? = runCatching {
        val uri = URI(if (raw.contains("://")) raw.trim() else "wss://${raw.trim()}")
        val scheme = when (uri.scheme.lowercase()) {
            "ws", "http" -> "ws"
            "wss", "https" -> "wss"
            else -> return null
        }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
        val query = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.map {
            val pair = it.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        val room = query.firstOrNull { it.first == "room" }?.second?.takeIf { it.isNotBlank() && it != "auto" } ?: fallbackRoom
        val encode = { value: String -> URLEncoder.encode(value, "UTF-8").replace("+", "%20") }
        val normalizedQuery = (query.filter { it.first != "room" } + ("room" to room))
            .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        val authority = uri.rawAuthority
        val path = uri.rawPath.takeUnless { it.isNullOrBlank() || it == "/" } ?: "/ws"
        WebSocket("$scheme://$authority$path?$normalizedQuery", room)
    }.getOrNull()
}
