package me.mochibit.createharmonics.audio.process

import me.mochibit.createharmonics.Logger.info
import me.mochibit.createharmonics.Logger.err
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages the lifecycle of external processes (FFmpeg, yt-dlp, etc.)
 * Ensures all processes are properly terminated when Minecraft closes.
 */
object ProcessLifecycleManager {
    private val processes = ConcurrentHashMap<Long, Process>()
    private val processIdGenerator = AtomicLong(0)

    /**
     * Register a process for lifecycle management.
     * Returns a unique ID for the process.
     */
    fun registerProcess(process: Process): Long {
        val id = processIdGenerator.incrementAndGet()
        processes[id] = process
        return id
    }

    /**
     * Unregister a process after it completes.
     */
    fun unregisterProcess(id: Long) {
        processes.remove(id)
    }

    /**
     * Destroy a specific process by ID.
     */
    fun destroyProcess(id: Long) {
        processes.remove(id)?.let { process ->
            try {
                if (process.isAlive) {
                    process.destroy()
                    // Give it a moment to terminate gracefully
                    Thread.sleep(100)
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            } catch (e: Exception) {
                err("Error destroying process $id: ${e.message}")
            }
        }
    }

    /**
     * Shutdown all managed processes.
     * Should be called when Minecraft is closing.
     */
    fun shutdownAll() {
        info("Shutting down ${processes.size} managed processes...")

        processes.keys.toList().forEach { id ->
            destroyProcess(id)
        }

        processes.clear()
        info("All managed processes shut down")
    }

    /**
     * Get the number of currently active processes.
     */
    fun getActiveProcessCount(): Int = processes.size
}

