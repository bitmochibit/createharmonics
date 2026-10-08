package me.mochibit.createharmonics.audio.upload

class UploadSession(val fileName: String) {
    @Volatile var progress = 0f
    @Volatile var finished = false
        private set

    fun finish() { finished = true }
}