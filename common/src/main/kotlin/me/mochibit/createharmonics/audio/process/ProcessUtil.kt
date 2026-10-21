package me.mochibit.createharmonics.audio.process

import kotlinx.coroutines.*
data class ProcessOutput(val exitCode: Int, val stdout: String, val stderr: String)

suspend fun runProcess(command: List<String>): ProcessOutput = withContext(Dispatchers.IO) {
    val process = ProcessBuilder(command).redirectErrorStream(false).start()
    val processId = ProcessLifecycleManager.registerProcess(process)
    try {
        coroutineScope {
            val watchdog = launch {
                try { awaitCancellation() } finally { process.destroyForcibly() }
            }
            val stdout = async { process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            val stderr = async { process.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() } }
            val out = stdout.await()
            val err = stderr.await()
            watchdog.cancel()
            ProcessOutput(process.waitFor(), out, err)
        }
    } finally {
        ProcessLifecycleManager.destroyProcess(processId)
    }
}