package me.mochibit.createharmonics.audio.upload

import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * Bridge data for connecting mc packet system and the mods http service
 *
 * @constructor Creates a new UploadTicket
 * @property token
 * @property playerUuid the player uuid
 * @property playerName the player name
 * @property expiresAt the expires at
 * @property uploadSession the upload session
 */
data class UploadTicket(
    val token: String,
    val playerUuid: UUID,
    val playerName: String,
    val expiresAt: Instant,
    val uploadSession: UploadSession
)

/**
 * Upload ticket registry
 * Singleton object — a single shared instance.
 *
 * @constructor Creates a new UploadTicketRegistry
 */
object UploadTicketRegistry {
    private val tickets = ConcurrentHashMap<String, UploadTicket>()
    private val random = SecureRandom()
    private const val TTL_SECONDS = 30L * 60
    private const val TOKEN_BYTES = 32


    /**
     * Issue
     *
     * @param playerUuid the player uuid
     * @param playerName the player name
     * @param session
     * @return the upload ticket
     */
    fun issue(playerUuid: UUID, playerName: String, session: UploadSession): UploadTicket {
        val tokenBytes = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes)
        return UploadTicket(
            token = token,
            playerUuid = playerUuid,
            playerName = playerName,
            expiresAt = Instant.now().plusSeconds(TTL_SECONDS),
            uploadSession = session
        ).also {
            tickets[token] = it
        }
    }

    /**
     * Consume a token to get a valid ticket
     *
     * @return Valid UploadTicket, otherwise null
     * @param token
     */
    fun consume(token: String): UploadTicket? {
        val ticket = tickets.remove(token) ?: return null
        if (ticket.uploadSession.cancelled || ticket.expiresAt.isBefore(Instant.now())) {
            ticket.uploadSession.finish()
            return null
        }
        return ticket
    }


    /**
     * Purge expired
     *
     */
    fun purgeExpired() {
        val now = Instant.now()
        val iterator = tickets.values.iterator()
        while (iterator.hasNext()) {
            val ticket = iterator.next()
            if (ticket.uploadSession.finished || ticket.expiresAt.isBefore(now)) {
                iterator.remove()
                ticket.uploadSession.finish()
            }
        }
    }

    /**
     * Stops the all
     *
     */
    fun finishAll() {
        tickets.values.forEach { it.uploadSession.cancel() }
        tickets.clear()
    }
}