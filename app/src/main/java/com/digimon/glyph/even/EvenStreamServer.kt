package com.digimon.glyph.even

import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ArrayBlockingQueue

/** Bounded local-only HTTP stream for an EHPK hosted by Even on the same phone. */
class EvenStreamServer(private val token: String, private val frame: () -> String,
                       private val press: (String, String) -> Boolean, port: Int = 8766) : AutoCloseable {
    private val socket = token.let {
        require(it.matches(Regex("[a-f0-9]{32}")))
        ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"))
    }
    val port: Int get() = socket.localPort
    @Volatile private var closed = false
    private val workers = ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, ArrayBlockingQueue(4))
    private val seen = LinkedHashMap<String, Long>()
    private val acceptor = Executors.newSingleThreadExecutor()
    init {
        acceptor.execute {
            while (!closed) try {
                val client = socket.accept().apply { soTimeout = 2000 }
                try { workers.execute { client.use { serve(it) } } } catch (_: java.util.concurrent.RejectedExecutionException) { client.close() }
            } catch (_: java.io.IOException) { if (closed) break }
        }
    }
    private fun line(input: BufferedInputStream): String {
        val out = StringBuilder()
        while (out.length <= 4096) {
            val b = input.read(); require(b >= 0) { "Incomplete request" }
            if (b == 10) return out.toString().trimEnd('\r')
            out.append(b.toChar())
        }
        error("Request line too long")
    }
    private fun serve(client: Socket) {
        try {
            val input = BufferedInputStream(client.getInputStream())
            val request = line(input).split(' ')
            require(request.size == 3 && request[2] == "HTTP/1.1")
            val headers = mutableMapOf<String, String>()
            var total = 0
            while (true) {
                val value = line(input); if (value.isEmpty()) break
                total += value.length; require(total <= 8192 && headers.size < 32)
                val index = value.indexOf(':'); require(index > 0)
                val key=value.substring(0,index).lowercase(); require(key !in headers)
                headers[key] = value.substring(index + 1).trim()
            }
            if (request[0] == "OPTIONS") { respond(client, 204, ""); return }
            val supplied = headers["authorization"].orEmpty()
            if (!MessageDigest.isEqual(supplied.toByteArray(), "Bearer $token".toByteArray())) { respond(client, 401, "{}"); return }
            when {
                request[0] == "GET" && request[1] == "/v1/frame" -> respond(client, 200, frame())
                request[0] == "POST" && request[1] == "/v1/press" -> {
                    require("transfer-encoding" !in headers)
                    val length = headers["content-length"]?.toIntOrNull() ?: error("Missing length")
                    require(length in 1..1024)
                    val bytes = ByteArray(length); var read = 0
                    while (read < length) { val n = input.read(bytes, read, length - read); require(n > 0); read += n }
                    val obj = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
                    require(obj.keySet() == setOf("session", "seq", "button", "romHash"))
                    val session = obj["session"].asString; val seq = obj["seq"].asString.toLong()
                    val button = obj["button"].asString; val hash = obj["romHash"].asString
                    require(session.matches(Regex("[a-f0-9-]{36}")) && seq in 1..9007199254740991L)
                    require(button in setOf("a","b","c","left","right","up","down","step","card"))
                    require(hash.matches(Regex("[a-f0-9]{64}")))
                    val accepted = synchronized(seen) {
                        val old = seen[session] ?: 0
                        if (seq <= old || seq != old + 1) false else {
                            if (seen.size >= 64 && session !in seen) seen.remove(seen.keys.first())
                            seen[session] = seq
                            press(button, hash)
                        }
                    }
                    respond(client, if (accepted) 200 else 409, "{\"accepted\":$accepted}")
                }
                else -> respond(client, 404, "{}")
            }
        } catch (_: Exception) { try { respond(client, 400, "{}")} catch (_: Exception) {} }
    }
    private fun respond(socket: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $code Response\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS\r\nAccess-Control-Allow-Headers: Authorization, Content-Type\r\nAccess-Control-Allow-Private-Network: true\r\n\r\n"
        socket.getOutputStream().apply { write(header.toByteArray()); write(bytes); flush() }
    }
    override fun close() { closed = true; socket.close(); acceptor.shutdownNow(); workers.shutdownNow() }
}
