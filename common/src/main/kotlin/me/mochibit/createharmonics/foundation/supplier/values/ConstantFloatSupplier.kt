package me.mochibit.createharmonics.foundation.supplier.values

class ConstantFloatSupplier(
    private val value: Float,
) : FloatSupplier {
    override fun getValue(): Float = value
}
