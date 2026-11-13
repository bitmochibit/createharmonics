package me.mochibit.createharmonics.audio.upload

import kotlinx.serialization.Serializable


/**
 * Audio upload response
 * Enum — a fixed set of named constants.
 *
 * @constructor Creates a new AudioUploadResponse
 * @property translationKey the translation key
 */
@Serializable
enum class AudioUploadResponse(val translationKey: String) {
    // Notifications

    /**
     * Non reachable
     *
     * @constructor Creates a new NON_REACHABLE
     */// Validation errors
    NON_REACHABLE("audio_upload.error.non_reachable"),

    /**
     * Server not running
     *
     * @constructor Creates a new SERVER_NOT_RUNNING
     */
    SERVER_NOT_RUNNING("audio_upload.error.server_not_running"),

    /**
     * Audio file limit
     *
     * @constructor Creates a new AUDIO_FILE_LIMIT
     */
    AUDIO_FILE_LIMIT("audio_upload.error.audio_file_limit"),

    /**
     * Block busy
     *
     * @constructor Creates a new BLOCK_BUSY
     */
    BLOCK_BUSY("audio_upload.error.block_busy"),

    /**
     * File too large
     *
     * @constructor Creates a new FILE_TOO_LARGE
     */// HTTP errors
    FILE_TOO_LARGE("audio_upload.error.file_too_large"),

    /**
     * File not found
     *
     * @constructor Creates a new FILE_NOT_FOUND
     */
    FILE_NOT_FOUND("audio_upload.error.file_not_found"),

    /**
     * Invalid input
     *
     * @constructor Creates a new INVALID_INPUT
     */
    INVALID_INPUT("audio_upload.error.invalid_input"),

    /**
     * Unauthorized
     *
     * @constructor Creates a new UNAUTHORIZED
     */
    UNAUTHORIZED("audio_upload.error.unauthorized"),

    /**
     * Invalid method
     *
     * @constructor Creates a new INVALID_METHOD
     */
    INVALID_METHOD("audio_upload.error.invalid_method"),

    /**
     * Quota exceeded
     *
     * @constructor Creates a new QUOTA_EXCEEDED
     */
    QUOTA_EXCEEDED("audio_upload.error.quota_exceeded"),

    /**
     * Internal error
     *
     * @constructor Creates a new INTERNAL_ERROR
     */
    INTERNAL_ERROR("audio_upload.error.internal_error"),
    ;
}