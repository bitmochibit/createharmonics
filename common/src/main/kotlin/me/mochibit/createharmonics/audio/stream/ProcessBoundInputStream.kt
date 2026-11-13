package me.mochibit.createharmonics.audio.stream

import me.mochibit.createharmonics.audio.process.ProcessLifecycleManager
import java.io.FilterInputStream
import java.io.InputStream

/**
 * Handles the bound input stream
 *
 * @constructor
 * @property processId the process id
 * @param stream
 */
class ProcessBoundInputStream(
    val processId: Long,
    stream: InputStream,
) : FilterInputStream(stream) {
    override fun close() {
        try {
            this.`in`.close()
        } finally {
            ProcessLifecycleManager.destroyProcess(processId)
        }
    }
}
