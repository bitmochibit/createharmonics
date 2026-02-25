package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.foundation.async.modLaunch
import me.mochibit.createharmonics.foundation.err
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object ProcessLifecycleManager {
    private val processes = ConcurrentHashMap<Long, Process>()

    fun registerProcess(process: Process): Long {
        val pid = process.pid()
        processes[pid] = process
        return pid
    }

    /**
     * Unregister a process without attempting to destroy it.
     * Use this when the process has already been terminated externally.
     */
    fun unregisterProcess(id: Long) {
        processes.remove(id)
    }

    fun destroyProcess(id: Long) =
        modLaunch(Dispatchers.IO) {
            processes.remove(id)?.let { process ->
                try {
                    if (process.isAlive) {
                        process.destroy()
                        // Wait up to 2 seconds for graceful shutdown
                        delay(2.seconds)
                        if (process.isAlive) {
                            process.destroyForcibly()
                        }
                    }
                } catch (e: Exception) {
                    "Error destroying process $id: ${e.message}".err()
                }
            }
        }

    /**
     * Synchronously destroy a process. Use this during shutdown to ensure cleanup completes.
     */
    fun destroyProcessBlocking(id: Long) {
        processes.remove(id)?.let { process ->
            try {
                if (process.isAlive) {
                    process.destroy()
                    // Wait up to 1 second for graceful shutdown
                    if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                        process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                }
            } catch (e: Exception) {
                "Error destroying process $id: ${e.message}".err()
            }
        }
    }

    fun shutdownAll() {
        processes.keys.toList().forEach { id ->
            destroyProcessBlocking(id)
        }

        processes.clear()
    }
}
