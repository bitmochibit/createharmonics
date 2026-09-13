package me.mochibit.createharmonics.audio.upload


import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Input sanitizer, welp
 */
object InputSanitizer {

    // mc usernames: 1-16 chars, [A-Za-z0-9_]
    private val PLAYER_NAME = Regex("^[A-Za-z0-9_]{1,16}$")

    /**
     * file ids, hex
     */
    private val FILE_ID = Regex("^[0-9a-fA-F-]{36}$")

    fun validatePlayerName(raw: String?): String =
        raw?.takeIf { PLAYER_NAME.matches(it) }
            ?: throw InvalidInputException("Invalid player name")

    fun validateFileId(raw: String?): String =
        raw?.takeIf { FILE_ID.matches(it) && runCatching { UUID.fromString(it) }.isSuccess }
            ?: throw InvalidInputException("Invalid file id")

    fun validateExtension(raw: String?, allowed: Set<String>): String {
        val ext = raw?.lowercase()
        if (ext == null || ext !in allowed) throw InvalidInputException("Extension not allowed")
        return ext
    }

    fun newFileId(): String = UUID.randomUUID().toString()

    /**
     * Resolves [segments] under [base] and guarantees, via canonical path
     * containment, that the result cannot escape [base].
     * this should be foolproof.
     */
    fun resolveContained(base: Path, vararg segments: String): Path {
        val baseReal = Files.createDirectories(base).toRealPath()
        var candidate = baseReal
        for (segment in segments) {
            if (segment.isBlank() || segment == "." || segment == "..") {
                throw InvalidInputException("Illegal path segment: $segment")
            }
            candidate = candidate.resolve(segment).normalize()
        }
        if (!candidate.startsWith(baseReal)) {
            throw InvalidInputException("Path traversal detected")
        }
        return candidate
    }
}

class InvalidInputException(message: String) : RuntimeException(message)