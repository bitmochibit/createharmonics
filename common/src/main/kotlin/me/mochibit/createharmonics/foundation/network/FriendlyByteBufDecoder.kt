package me.mochibit.createharmonics.foundation.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.modules.EmptySerializersModule
import net.minecraft.network.FriendlyByteBuf

@OptIn(ExperimentalSerializationApi::class)
class FriendlyByteBufDecoder(
    private val buf: FriendlyByteBuf,
) : AbstractDecoder() {
    private var elementIndex = 0
    override val serializersModule = EmptySerializersModule()

    override fun decodeBoolean() = buf.readBoolean()

    override fun decodeByte() = buf.readByte()

    override fun decodeShort() = buf.readShort()

    override fun decodeInt() = buf.readVarInt()

    override fun decodeLong() = buf.readLong()

    override fun decodeFloat() = buf.readFloat()

    override fun decodeDouble() = buf.readDouble()

    override fun decodeString() = buf.readUtf()

    override fun decodeEnum(enumDescriptor: SerialDescriptor) = buf.readVarInt()

    override fun decodeNull(): Nothing? = null

    override fun decodeNotNullMark() = buf.readBoolean()

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        if (elementIndex == descriptor.elementsCount) return CompositeDecoder.Companion.DECODE_DONE
        return elementIndex++
    }

    override fun beginStructure(descriptor: SerialDescriptor) = FriendlyByteBufDecoder(buf)
}
