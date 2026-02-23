package me.mochibit.createharmonics.foundation.math

class ConstantFloatSupplier(
    private val value: Float,
) : FloatSupplier {
    override fun getValue(): Float = value
}
