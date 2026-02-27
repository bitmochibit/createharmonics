package me.mochibit.createharmonics.foundation.supplier.values

class DynamicFloatSupplier(
    val supplier: () -> Float,
) : FloatSupplier {
    override fun getValue(): Float = supplier()
}
