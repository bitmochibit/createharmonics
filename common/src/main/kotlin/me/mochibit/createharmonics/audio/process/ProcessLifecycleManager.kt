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

/**
 * Handles the lifecycle manager
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new ProcessLifecycleManager
 */
object ProcessLifecycleManager {
    private val processes = ConcurrentHashMap<Long, Process>()

    /**
     * Adds the process
     *
     * @param process
     * @return the long
     */
    fun registerProcess(process: Process): Long {
        val pid = process.pid()
        processes[pid] = process
        return pid
    }

    /**
     * Unregister a process without attempting to destroy it.
     * Use this when the process has already been terminated externally.
     *
     * @param id
     */
    fun unregisterProcess(id: Long) {
        processes.remove(id)
    }

    /**
     * Returns `true` if alive
     *
     * @param id
     * @return `true` if the condition holds, `false` otherwise
     */
    fun isAlive(id: Long): Boolean = processes[id]?.isAlive ?: false

    /**
     * Returns the exit code
     *
     * @param id
     * @return the int
     */
    fun getExitCode(id: Long): Int? {
        val process = processes[id] ?: return null
        return if (process.isAlive) {
            null
        } else {
            process.exitValue()
        }
    }

    /**
     * Removes the process
     *
     * @param id
     * @return the job
     */
    fun destroyProcess(id: Long) =
        ModCoroutineScope.launch(Dispatchers.IO) {
            val process = processes.remove(id) ?: return@launch
            try {
                if (process.isAlive) {
                    process.destroyForcibly()
                    withContext(Dispatchers.IO) {
                        process.waitFor()
                    }
                }
            } catch (e: Exception) {
                "Error destroying process $id: ${e.message}".err()
            }
        }

    /**
     * Stops the all
     *
     */
    fun shutdownAll() {
        processes.keys.toList().forEach { id ->
            processes.remove(id)?.let { process ->
                try {
                    if (process.isAlive) {
                        process.destroyForcibly()
                        process.waitFor()
                    }
                } catch (e: Exception) {
                    "Error destroying process $id: ${e.message}".err()
                }
            }
        }
        processes.clear()
    }
}
