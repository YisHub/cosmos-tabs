package com.yishub.cosmostabs.test.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Solo en la prueba: Minecraft ignora el movimiento del raton si su ventana no tiene el foco,
 * y la prueba no debe depender de que el usuario no este usando otra ventana.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftFocusMixin {
	@Inject(method = "isWindowActive", at = @At("HEAD"), cancellable = true)
	private void cosmostabsTest$alwaysActive(CallbackInfoReturnable<Boolean> cir) {
		cir.setReturnValue(true);
	}
}
