package com.yishub.cosmostabs.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.yishub.cosmostabs.CosmosTabs;
import com.yishub.cosmostabs.client.CosmosTabsClient;
import com.yishub.cosmostabs.client.MovableWindow;
import com.yishub.cosmostabs.client.WindowPositions;
import com.yishub.cosmostabs.net.SwitchTabPayload;
import com.yishub.cosmostabs.net.TabsPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Convierte las pantallas de contenedores en una ventana con una barra superior: la barra se
 * arrastra para mover la ventana (clic derecho la vuelve al centro) y, si el servidor tiene el
 * mod, muestra una pestaña por cada contenedor cercano.
 *
 * <p>La ventana no cambia su posicion interna (leftPos/topPos): ScreenMixin la dibuja trasladada y
 * MouseHandlerMixin le resta el desplazamiento al raton. Aqui todo se calcula en esas coordenadas
 * "como si estuviera centrada".
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin implements MovableWindow {
	@Unique private static final int TAB_W = 22;
	@Unique private static final int TAB_H = 20;
	@Unique private static final int GRIP_H = 10;

	@Shadow @Final protected int imageWidth;
	@Shadow @Final protected int imageHeight;
	@Shadow @Final protected AbstractContainerMenu menu;
	@Shadow protected int leftPos;
	@Shadow protected int topPos;

	@Unique private double cosmostabs$offsetX;
	@Unique private double cosmostabs$offsetY;
	@Unique private boolean cosmostabs$dragging;

	@Override
	public int cosmostabs$offsetX() {
		return (int) Math.round(this.cosmostabs$offsetX);
	}

	@Override
	public int cosmostabs$offsetY() {
		return (int) Math.round(this.cosmostabs$offsetY);
	}

	@Unique
	private boolean cosmostabs$enabled() {
		Object self = this;
		return !(self instanceof CreativeModeInventoryScreen) && !(self instanceof AbstractRecipeBookScreen<?>);
	}

	@Unique
	private Screen cosmostabs$screen() {
		return (Screen) (Object) this;
	}

	@Unique
	private String cosmostabs$key() {
		return this.getClass().getName();
	}

	@Unique
	private List<TabsPayload.Tab> cosmostabs$tabs() {
		return CosmosTabsClient.tabsFor(this.menu.containerId);
	}

	@Unique
	private int cosmostabs$barHeight() {
		return this.cosmostabs$tabs().isEmpty() ? GRIP_H : TAB_H;
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void cosmostabs$loadOffset(CallbackInfo ci) {
		if (!this.cosmostabs$enabled()) {
			return;
		}
		int[] offset = WindowPositions.get(this.cosmostabs$key());
		this.cosmostabs$setOffset(offset[0], offset[1]);
	}

	/** Fija el desplazamiento sin dejar que la ventana (ni su barra) se salga de la pantalla. */
	@Unique
	private void cosmostabs$setOffset(double dx, double dy) {
		Screen screen = this.cosmostabs$screen();
		this.cosmostabs$offsetX = Math.max(-this.leftPos, Math.min(dx, screen.width - this.imageWidth - this.leftPos));
		this.cosmostabs$offsetY = Math.max(TAB_H - this.topPos, Math.min(dy, screen.height - this.imageHeight - this.topPos));
	}

	@Unique
	private void cosmostabs$saveOffset() {
		WindowPositions.set(this.cosmostabs$key(), this.cosmostabs$offsetX(), this.cosmostabs$offsetY());
	}

	@Unique
	private boolean cosmostabs$inBar(double mx, double my) {
		return mx >= this.leftPos && mx < this.leftPos + this.imageWidth
				&& my >= this.topPos - this.cosmostabs$barHeight() && my < this.topPos;
	}

	/** Indice de la pestaña bajo el raton, o -1. */
	@Unique
	private int cosmostabs$tabAt(double mx, double my) {
		if (my < this.topPos - TAB_H || my >= this.topPos || mx < this.leftPos) {
			return -1;
		}
		int i = (int) ((mx - this.leftPos) / (TAB_W + 1));
		boolean onGap = (mx - this.leftPos) % (TAB_W + 1) >= TAB_W;
		return i < this.cosmostabs$tabs().size() && !onGap ? i : -1;
	}

	@Inject(method = "extractContents", at = @At("TAIL"))
	private void cosmostabs$drawBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!this.cosmostabs$enabled()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		List<TabsPayload.Tab> tabs = this.cosmostabs$tabs();
		int right = this.leftPos + this.imageWidth;
		int tabsEnd = this.leftPos + tabs.size() * (TAB_W + 1);

		// Zona de arrastre: lo que queda de barra a la derecha de las pestañas.
		int gripTop = this.topPos - GRIP_H;
		boolean gripHover = this.cosmostabs$dragging
				|| (this.cosmostabs$inBar(mouseX, mouseY) && this.cosmostabs$tabAt(mouseX, mouseY) < 0);
		if (tabsEnd < right) {
			graphics.fill(tabsEnd, gripTop, right, this.topPos, gripHover ? 0xFF6F6F6F : 0xFF555555);
			graphics.outline(tabsEnd, gripTop, right - tabsEnd, GRIP_H, 0xFF373737);
			int cx = (tabsEnd + right) / 2;
			for (int i = -2; i <= 2; i++) {
				graphics.fill(cx + i * 4, gripTop + 4, cx + i * 4 + 2, gripTop + 6, 0xFFC6C6C6);
			}
		}

		int hovered = this.cosmostabs$tabAt(mouseX, mouseY);
		for (int i = 0; i < tabs.size(); i++) {
			TabsPayload.Tab tab = tabs.get(i);
			int x = this.leftPos + i * (TAB_W + 1);
			int y = this.topPos - TAB_H;
			int bg = tab.current() ? 0xFFC6C6C6 : (i == hovered ? 0xFFA8A8A8 : 0xFF8B8B8B);
			graphics.fill(x, y, x + TAB_W, this.topPos + (tab.current() ? 1 : 0), bg);
			graphics.fill(x, y, x + TAB_W, y + 1, 0xFFFFFFFF);
			graphics.fill(x, y, x + 1, this.topPos, 0xFFFFFFFF);
			graphics.fill(x + TAB_W - 1, y, x + TAB_W, this.topPos, 0xFF373737);
			graphics.item(tab.icon(), x + 3, y + 2);
		}
		if (hovered >= 0) {
			graphics.setTooltipForNextFrame(font, tabs.get(hovered).name(), mouseX, mouseY);
		} else if (gripHover && !this.cosmostabs$dragging) {
			graphics.setTooltipForNextFrame(font,
					Component.literal("Arrastra para mover · clic derecho: centrar"), mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$clickBar(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		CosmosTabs.debug("clic ({}, {}) boton={} desplazamiento=({}, {}) barra={} pestaña={}", event.x(), event.y(),
				event.button(), this.cosmostabs$offsetX(), this.cosmostabs$offsetY(),
				this.cosmostabs$inBar(event.x(), event.y()), this.cosmostabs$tabAt(event.x(), event.y()));
		if (!this.cosmostabs$enabled() || !this.cosmostabs$inBar(event.x(), event.y())) {
			return;
		}
		cir.setReturnValue(true);
		int tab = this.cosmostabs$tabAt(event.x(), event.y());
		if (tab >= 0) {
			TabsPayload.Tab target = this.cosmostabs$tabs().get(tab);
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !target.current() && this.menu.getCarried().isEmpty()) {
				CosmosTabs.debug("enviando cambio a {}", target.pos().toShortString());
				ClientPlayNetworking.send(new SwitchTabPayload(target.pos()));
			}
		} else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.cosmostabs$setOffset(0, 0);
			this.cosmostabs$saveOffset();
		} else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.cosmostabs$dragging = true;
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$dragWindow(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (this.cosmostabs$dragging) {
			// Se usan los deltas: la posicion del evento ya viene corregida con el desplazamiento que estamos cambiando.
			this.cosmostabs$setOffset(this.cosmostabs$offsetX + dx, this.cosmostabs$offsetY + dy);
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$dropWindow(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (this.cosmostabs$dragging) {
			this.cosmostabs$dragging = false;
			this.cosmostabs$saveOffset();
			cir.setReturnValue(true);
		} else if (this.cosmostabs$enabled() && this.cosmostabs$inBar(event.x(), event.y())) {
			// Que soltar el clic sobre la barra no cuente como "clic fuera" (tiraria el objeto que llevas).
			cir.setReturnValue(true);
		}
	}
}
