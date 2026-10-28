package com.yishub.cosmostabs.client.mixin;

import com.yishub.cosmostabs.client.MovableWindow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dibuja la ventana desplazada: traslada todo el render de la pantalla (incluidos tooltips), salvo
 * el fondo oscurecido del mundo, que ocupa toda la pantalla y se deshace la traslacion.
 */
@Mixin(Screen.class)
public abstract class ScreenMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"))
	private void cosmostabs$pushOffset(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if ((Object) this instanceof MovableWindow window) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(window.cosmostabs$offsetX(), window.cosmostabs$offsetY());
		}
	}

	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
	private void cosmostabs$popOffset(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if ((Object) this instanceof MovableWindow) {
			graphics.pose().popMatrix();
		}
	}

	@Inject(method = "extractBackground", at = @At("HEAD"))
	private void cosmostabs$undoOffsetForBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if ((Object) this instanceof MovableWindow window) {
			graphics.pose().pushMatrix();
			graphics.pose().translate(-window.cosmostabs$offsetX(), -window.cosmostabs$offsetY());
		}
	}

	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void cosmostabs$redoOffsetAfterBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if ((Object) this instanceof MovableWindow) {
			graphics.pose().popMatrix();
		}
	}
}
