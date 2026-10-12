package com.digimon.glyph.owl

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Pushes native frames with one outstanding message per client; a slow launcher skips old frames. */
class OwlBridge(
    private val context: Context,
    private val start: () -> Unit,
    private val button: (String) -> Unit,
    private val status: () -> Pair<Boolean, Boolean>,
) {
    private data class Client(val messenger: Messenger, val uid: Int, val death: IBinder.DeathRecipient, var sent: Long = -1, var waiting: Boolean = false)
    private val handler = Handler(Looper.getMainLooper())
    private val clients = LinkedHashMap<IBinder, Client>()
    private val pending = AtomicReference<Bundle?>()
    private val scheduled = AtomicBoolean(false)
    @Volatile var observed = false
        private set
    private var sequence = 0L
    private var latest = Bundle().apply {
        putInt("version", OwlProtocol.VERSION)
        putBoolean("ready", false)
        putBoolean("frozen", false)
        putInt("width", 32)
        putInt("height", 24)
        putStringArrayList("rows", arrayListOf())
    }
    val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val reply = msg.replyTo ?: return
            // Only the two launcher identities may observe/control this emulator.
            val packages = context.packageManager.getPackagesForUid(msg.sendingUid).orEmpty()
            if (packages.none { it == "dev.omalauncher.hermes" || it == "dev.omalauncher" }) return
            val binder = reply.binder
            if (msg.what == OwlProtocol.SUBSCRIBE) {
                if (msg.arg1 != OwlProtocol.VERSION) {
                    try { reply.send(Message.obtain(null, OwlProtocol.ERROR).apply { data = Bundle().apply { putString("error", "Update both apps: incompatible bridge version") } }) } catch (_: RemoteException) {}
                    return
                }
                if (binder !in clients && clients.size < 4) {
                    val death = IBinder.DeathRecipient { handler.post { remove(binder) } }
                    try { binder.linkToDeath(death, 0) } catch (_: RemoteException) { return }
                    clients[binder] = Client(reply, msg.sendingUid, death)
                }
                observed = clients.isNotEmpty()
                if (binder !in clients) return
                start()
                updateStatus()
                send(clients.getValue(binder))
                handler.removeCallbacks(checkStatus)
                handler.postDelayed(checkStatus, 1000)
                return
            }
            val client = clients[binder]?.takeIf { it.uid == msg.sendingUid } ?: return
            when (msg.what) {
                OwlProtocol.UNSUBSCRIBE -> remove(binder)
                OwlProtocol.ACK -> {
                    if (msg.data.getLong("sequence", -2) == client.sent) {
                        client.waiting = false
                        send(client)
                    }
                }
                OwlProtocol.BUTTON -> {
                    val key = msg.data.getString("button") ?: return
                    if (key in setOf("a", "b", "c")) button(key)
                }
            }
        }
    })

    private val checkStatus = object : Runnable {
        override fun run() {
            if (!observed) return
            updateStatus()
            handler.postDelayed(this, 1000)
        }
    }

    /** Called on the emulator thread, at its own frame rate. Only one main-thread delivery is queued. */
    fun publish(bitmap: Bitmap) {
        if (!observed) return
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        pending.set(Bundle().apply {
            putInt("version", OwlProtocol.VERSION)
            putInt("width", bitmap.width)
            putInt("height", bitmap.height)
            putStringArrayList("rows", OwlPixels.rows(pixels, bitmap.width, bitmap.height))
        })
        if (scheduled.compareAndSet(false, true)) handler.post(deliver)
    }

    private val deliver = object : Runnable {
        override fun run() {
            pending.getAndSet(null)?.let { frame ->
                latest = frame
                val (ready, frozen) = status()
                latest.putBoolean("ready", ready)
                latest.putBoolean("frozen", frozen)
                sequence++
                clients.values.toList().forEach(::send)
            }
            scheduled.set(false)
            if (pending.get() != null && scheduled.compareAndSet(false, true)) handler.post(this)
        }
    }

    private fun updateStatus() {
        val (ready, frozen) = status()
        if (ready == latest.getBoolean("ready") && frozen == latest.getBoolean("frozen")) return
        latest = Bundle(latest).apply {
            putBoolean("ready", ready)
            putBoolean("frozen", frozen)
        }
        sequence++
        clients.values.toList().forEach(::send)
    }

    private fun send(client: Client) {
        if (client.waiting || client.sent == sequence) return
        client.sent = sequence
        client.waiting = true
        try {
            client.messenger.send(Message.obtain(null, OwlProtocol.FRAME).apply {
                data = Bundle(latest).apply { putLong("sequence", sequence) }
            })
        } catch (_: RemoteException) { remove(client.messenger.binder) }
    }

    private fun remove(binder: IBinder) {
        clients.remove(binder)?.let { binder.unlinkToDeath(it.death, 0) }
        observed = clients.isNotEmpty()
        if (!observed) handler.removeCallbacks(checkStatus)
    }

    fun disconnect() {
        clients.keys.toList().forEach(::remove)
        pending.set(null)
        handler.removeCallbacks(deliver)
        scheduled.set(false)
    }
}
