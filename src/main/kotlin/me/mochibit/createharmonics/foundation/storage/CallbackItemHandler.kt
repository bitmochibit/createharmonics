package me.mochibit.createharmonics.foundation.storage

import net.minecraftforge.items.ItemStackHandler

open class CallbackItemHandler(
    size: Int,
    var onLoadEvent: () -> Unit = {},
    var onChangeEvent: (slot: Int) -> Unit = {},
) : ItemStackHandler(size) {
    override fun onLoad() {
        onLoadEvent()
    }

    override fun onContentsChanged(slot: Int) {
        onChangeEvent(slot)
    }
}
