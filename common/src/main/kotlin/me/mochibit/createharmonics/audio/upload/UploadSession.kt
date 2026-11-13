package me.mochibit.createharmonics.audio.upload

import java.io.IOException

/**
 * Upload cancelled exception
 *
 * @constructor Creates a new UploadCancelledException
 */
class UploadCancelledException : IOException("Upload cancelled")

/**
 * Upload session
 *
 * @constructor Creates a new UploadSession
 * @property fileName the file name
 */
class UploadSession(val fileName: String) {
    @Volatile var progress = 0f
    @Volatile var finished = false
        private set
    @Volatile var cancelled = false
        private set

    /**
     * Stops
     *
     */
    fun finish() { finished = true }

    /**
     * Stops
     *
     */
    fun cancel() {
        cancelled = true
        finished = true
    }

    /**
     * Throw if cancelled
     *
     * @throws UploadCancelledException
     */
    fun throwIfCancelled() {
        if (cancelled) throw UploadCancelledException()
    }
}