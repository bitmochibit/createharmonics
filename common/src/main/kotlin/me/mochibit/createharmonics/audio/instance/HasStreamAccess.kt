package me.mochibit.createharmonics.audio.instance

import net.minecraft.client.sounds.AudioStream
import java.util.concurrent.CompletableFuture

interface HasStreamAccess {
    val audioStream: CompletableFuture<AudioStream>
}
