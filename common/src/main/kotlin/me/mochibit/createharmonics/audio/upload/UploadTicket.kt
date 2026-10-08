package me.mochibit.createharmonics.audio.upload

import java.security.SecureRandom
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap


/**
 * Bridge data for connecting mc packet system and the mods http service
 */
data class UploadTicket(
    val token: String,
    val playerUuid: UUID,
    val playerName: String,
    val expiresAt: Instant,
    val uploadSession: UploadSession
)

object UploadTicketRegistry {
    private val tickets = ConcurrentHashMap<String, UploadTicket>()
    private val random = SecureRandom()
    private const val TTL_SECONDS = 60L
    private const val TOKEN_BYTES = 32


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
     * @return Valid UploadTicket, otherwise null
     */
    fun consume(token: String): UploadTicket? {
        val ticket = tickets.remove(token) ?: return null
        if (ticket.expiresAt.isBefore(Instant.now())) {
            ticket.uploadSession.finish()
            return null
        }
        return ticket
    }

    fun purgeExpired() {
        val now = Instant.now()
        val iterator = tickets.values.iterator()
        while(iterator.hasNext()) {
            val ticket = iterator.next()
            if (ticket.expiresAt.isBefore(now)) {
                iterator.remove()
                ticket.uploadSession.finish()
            }
        }
    }

    fun finishAll() {
        tickets.values.forEach { it.uploadSession.finish() }
        tickets.clear()
    }
}