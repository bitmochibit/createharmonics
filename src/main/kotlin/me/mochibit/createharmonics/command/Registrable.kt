package me.mochibit.createharmonics.command

/**
 * Interface for helping the registration and keeping contracts.
 * TODO: A nice, reflection based registrar
 */
interface Registrable<RegisterContext> {
    fun register(ctx: RegisterContext)
}