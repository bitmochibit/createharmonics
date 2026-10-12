package me.mochibit.createharmonics.audio.upload

import java.io.IOException

class UploadCancelledException : IOException("Upload cancelled")

class UploadSession(val fileName: String) {
    @Volatile var progress = 0f
    @Volatile var finished = false
        private set
    @Volatile var cancelled = false
        private set

    fun finish() { finished = true }

    fun cancel() {
        cancelled = true
        finished = true
    }

    fun throwIfCancelled() {
        if (cancelled) throw UploadCancelledException()
    }
}