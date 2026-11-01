package me.mochibit.createharmonics.mixin;

import net.createmod.catnip.net.packets.ServerboundConfigPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerboundConfigPacket.class, remap = false)
public abstract class ServerboundConfigPacketMixin {

    @Inject(method = "serialize", at = @At("HEAD"), cancellable = true)
    private void createharmonics$serialize(Object value, CallbackInfoReturnable<String> cir) {
        if (value instanceof Long l) cir.setReturnValue(Long.toString(l));
        else if (value instanceof String s) cir.setReturnValue(s);
    }

    @Inject(method = "deserialize", at = @At("HEAD"), cancellable = true)
    private static void createharmonics$deserialize(Object type, String sValue, CallbackInfoReturnable<Object> cir) {
        if (type instanceof Long) cir.setReturnValue(Long.parseLong(sValue));
        else if (type instanceof String) cir.setReturnValue(sValue);
    }
}