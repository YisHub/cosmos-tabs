package com.yishub.cosmostabs.client.mixin;

import com.mojang.blaze3d.platform.Window;
import com.yishub.cosmostabs.client.MovableWindow;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Todas las coordenadas de raton que reciben las pantallas salen de aqui: se les quita el desplazamiento. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"), cancellable = true)
	private void cosmostabs$shiftX(Window window, CallbackInfoReturnable<Double> cir) {
		int dx = MovableWindow.currentOffset()[0];
		if (dx != 0) {
			cir.setReturnValue(cir.getReturnValue() - dx);
		}
	}

	@Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("RETURN"), cancellable = true)
	private void cosmostabs$shiftY(Window window, CallbackInfoReturnable<Double> cir) {
		int dy = MovableWindow.currentOffset()[1];
		if (dy != 0) {
			cir.setReturnValue(cir.getReturnValue() - dy);
		}
	}
}
