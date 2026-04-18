package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.createharmonics.foundation.async.ModCoroutineScope
import me.mochibit.createharmonics.foundation.err
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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

    fun isAlive(id: Long): Boolean = processes[id]?.isAlive ?: false

    fun getExitCode(id: Long): Int? {
        val process = processes[id] ?: return null
        return if (process.isAlive) {
            null
        } else {
            process.exitValue()
        }
    }

    fun destroyProcess(id: Long) =
        ModCoroutineScope.launch(Dispatchers.IO) {
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

    private suspend fun destroyProcessBlockingSuspend(id: Long) {
        processes.remove(id)?.let { process ->
            try {
                if (process.isAlive) {
                    process.destroy()

                    val timedOut =
                        withContext(Dispatchers.IO) {
                            !process.waitFor(1, TimeUnit.SECONDS)
                        }

                    if (timedOut) {
                        process.destroyForcibly()

                        withContext(Dispatchers.IO) {
                            process.waitFor()
                        }
                    }
                }
            } catch (e: Exception) {
                "Error destroying process $id: ${e.message}".err()
            }
        }
    }

    suspend fun shutdownAll() {
        processes.keys.toList().forEach { id ->
            destroyProcessBlockingSuspend(id)
        }
        processes.clear()
    }
}
