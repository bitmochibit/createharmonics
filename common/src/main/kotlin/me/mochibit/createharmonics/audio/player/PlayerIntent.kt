package me.mochibit.createharmonics.audio.player

sealed interface PlayerIntent {
    data object Play : PlayerIntent

    data object Pause : PlayerIntent

    data object Stop : PlayerIntent

    data object AudioHanged : PlayerIntent

    data object AudioFinished : PlayerIntent

    data object Shutdown : PlayerIntent

    data class Seek(
        val position: Double,
    ) : PlayerIntent

    data class NewRequest(
        val req: AudioRequest,
    ) : PlayerIntent
}
