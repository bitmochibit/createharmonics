package me.mochibit.createharmonics.audio.upload

import kotlinx.serialization.Serializable


@Serializable
enum class AudioUploadResponse(val translationKey: String) {
    // Notifications

    // Errors
    NON_REACHABLE("audio_upload.error.non_reachable"),
    SERVER_NOT_RUNNING("audio_upload.error.server_not_running"),
    AUDIO_FILE_LIMIT("audio_upload.error.audio_file_limit"),
    BLOCK_BUSY("audio_upload.error.block_busy"),
    ;
}