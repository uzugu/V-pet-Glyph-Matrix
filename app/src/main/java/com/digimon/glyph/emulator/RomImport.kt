package com.digimon.glyph.emulator

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** Bounded, streaming import. Caller performs IO off the Android main thread. */
object RomImport {
    data class Candidate(val name: String, val bytes: ByteArray, val device: RomCatalog.Device)
    fun readRomBytes(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Import cancelled")
            val n = input.read(buffer, 0, minOf(buffer.size, RomCatalog.MAX_ROM_BYTES + 1 - output.size()))
            if (n < 0) break
            output.write(buffer, 0, n)
            require(output.size() <= RomCatalog.MAX_ROM_BYTES) { "ROM exceeds supported size" }
        }
        return output.toByteArray()
    }
    fun read(input: InputStream, fileName: String): List<Candidate> {
        if (!fileName.endsWith(".zip", true)) {
            val bytes = readRomBytes(input)
            val device = RomCatalog.identify(bytes)
            return listOf(Candidate(fileName.substringAfterLast('/').substringBeforeLast('.'), bytes, device))
        }
        val found = mutableListOf<Candidate>()
        ZipInputStream(input).use { zip ->
            var total = 0L
            var entries = 0
            val buffer = ByteArray(8192)
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= 4096) { "Archive has too many entries" }
                val selected = !entry.isDirectory && entry.name.endsWith(".bin", true)
                val output = ByteArrayOutputStream()
                var size = 0L
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("Import cancelled")
                    val n = zip.read(buffer)
                    if (n < 0) break
                    total += n; size += n
                    require(total <= 256L * 1024 * 1024) { "Archive expands beyond 256 MB" }
                    if (selected && size <= RomCatalog.MAX_ROM_BYTES) output.write(buffer, 0, n)
                }
                if (selected && size <= RomCatalog.MAX_ROM_BYTES) {
                    val bytes = output.toByteArray()
                    val device = runCatching { RomCatalog.identify(bytes) }.getOrNull()
                    if (device != null) {
                        require(found.size < 64) { "Archive has too many supported ROMs" }
                        found += Candidate(entry.name, bytes, device)
                    }
                }
            }
        }
        require(found.isNotEmpty()) { "No supported ROM found in archive" }
        return found
    }
}
