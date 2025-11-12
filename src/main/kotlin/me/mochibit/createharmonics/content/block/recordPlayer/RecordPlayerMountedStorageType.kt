package me.mochibit.createharmonics.content.block.recordPlayer

import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorage
import com.simibubi.create.api.contraption.storage.item.simple.SimpleMountedStorageType
import net.minecraftforge.items.IItemHandler

class RecordPlayerMountedStorageType :
    SimpleMountedStorageType<RecordPlayerMountedStorage>(RecordPlayerMountedStorage.CODEC) {
    override fun createStorage(handler: IItemHandler): SimpleMountedStorage {
        return RecordPlayerMountedStorage(handler)
    }
}