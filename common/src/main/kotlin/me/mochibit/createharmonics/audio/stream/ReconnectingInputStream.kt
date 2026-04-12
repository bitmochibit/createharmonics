package me.mochibit.createharmonics.audio.stream

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import java.io.IOException
import java.io.InputStream

class ReconnectingInputStream(
    initialStream: InputStream,
    private val reconnect: suspend () -> InputStream?,
    private val shouldReconnect: () -> Boolean = { true },
    private val baseRetryDelay: Long = 5_000,
    private val maxRetryDelay: Long = 60_000,
) : InputStream() {
    @Volatile private var current: InputStream = initialStream

    @Volatile private var isClosed = false

    @Volatile private var isReconnecting = false

    private fun scheduleReconnect() {
        if (isReconnecting || isClosed) return
        isReconnecting = true

        modLaunch(Dispatchers.IO) {
            var delay = baseRetryDelay
            while (!isClosed) {
                delay(delay)
                "Stream failed, retrying..".err()

                val newStream = reconnect()
                if (newStream != null) {
                    try {
                        current.close()
                    } catch (_: Exception) {
                    }
                    current = newStream
                    isReconnecting = false
                    return@modLaunch
                }

                delay = minOf(delay * 2, maxRetryDelay)
            }
        }
    }

    override fun read(): Int {
        val buf = ByteArray(1)
        val n = read(buf, 0, 1)
        return if (n == -1) -1 else buf[0].toInt() and 0xFF
    }

    override fun read(
        b: ByteArray,
        off: Int,
        len: Int,
    ): Int {
        if (isClosed) return -1
        if (isReconnecting) return 0

        return try {
            val result = current.read(b, off, len)
            if (result == -1) {
                if (shouldReconnect()) {
                    scheduleReconnect()
                    0
                } else {
                    -1
                }
            } else {
                result
            }
        } catch (_: IOException) {
            if (shouldReconnect()) {
                scheduleReconnect()
                0
            } else {
                -1
            }
        }
    }

    override fun available(): Int =
        if (isReconnecting || isClosed) {
            0
        } else {
            try {
                current.available()
            } catch (_: Exception) {
                0
            }
        }

    override fun close() {
        isClosed = true
        try {
            current.close()
        } catch (_: Exception) {
        }
    }
}
