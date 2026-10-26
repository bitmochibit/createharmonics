package me.mochibit.createharmonics.audio.upload

import kotlinx.serialization.Serializable


@Serializable
enum class AudioUploadResponse(val translationKey: String) {
    // Notifications

    // Validation errors
    NON_REACHABLE("audio_upload.error.non_reachable"),
    SERVER_NOT_RUNNING("audio_upload.error.server_not_running"),
    AUDIO_FILE_LIMIT("audio_upload.error.audio_file_limit"),
    BLOCK_BUSY("audio_upload.error.block_busy"),

    // HTTP errors
    FILE_TOO_LARGE("audio_upload.error.file_too_large"),
    FILE_NOT_FOUND("audio_upload.error.file_not_found"),
    INVALID_INPUT("audio_upload.error.invalid_input"),

    UNAUTHORIZED("audio_upload.error.unauthorized"),

    INVALID_METHOD("audio_upload.error.invalid_method"),
    QUOTA_EXCEEDED("audio_upload.error.quota_exceeded"),
    INTERNAL_ERROR("audio_upload.error.internal_error"),
    ;
}