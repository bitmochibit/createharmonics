package me.mochibit.createharmonics.foundation.math

class DynamicFloatSupplier(
    val supplier: () -> Float,
) : FloatSupplier {
    override fun getValue(): Float = supplier()
}
