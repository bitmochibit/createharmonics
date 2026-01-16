package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.mochibit.createharmonics.Logger.err
import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.coroutine.launchModCoroutine
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object ProcessLifecycleManager {
    private val processes = ConcurrentHashMap<Long, Process>()

    fun registerProcess(process: Process): Long {
        val pid = process.pid()
        processes[pid] = process
        info("Registered process $pid (total: ${processes.size})")
        return pid
    }

    /**
     * Unregister a process without attempting to destroy it.
     * Use this when the process has already been terminated externally.
     */
    fun unregisterProcess(id: Long) {
        if (processes.remove(id) != null) {
            info("Unregistered process $id (remaining: ${processes.size})")
        }
    }

    fun destroyProcess(id: Long) =
        launchModCoroutine(Dispatchers.IO) {
            processes.remove(id)?.let { process ->
                try {
                    if (process.isAlive) {
                        process.destroy()
                        // Wait up to 2 seconds for graceful shutdown
                        delay(2.seconds)
                        if (process.isAlive) {
                            info("Force killing process $id")
                            process.destroyForcibly()
                        }
                    }
                    info("Destroyed process $id (remaining: ${processes.size})")
                } catch (e: Exception) {
                    err("Error destroying process $id: ${e.message}")
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
                        info("Force killing process $id")
                        process.destroyForcibly()
                        process.waitFor(500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    }
                }
                info("Destroyed process $id (remaining: ${processes.size})")
            } catch (e: Exception) {
                err("Error destroying process $id: ${e.message}")
            }
        }
    }

    fun shutdownAll() {
        info("Shutting down ${processes.size} managed processes...")

        // Use blocking destroy during shutdown to ensure cleanup completes
        processes.keys.toList().forEach { id ->
            destroyProcessBlocking(id)
        }

        processes.clear()
        info("All managed processes shut down")
    }
}
