package com.yishub.cosmostabs.client.mixin;

import com.yishub.cosmostabs.client.CosmosTabsClient;
import com.yishub.cosmostabs.client.WindowPositions;
import com.yishub.cosmostabs.net.SwitchTabPayload;
import com.yishub.cosmostabs.net.TabsPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
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
 * Convierte las pantallas de contenedores en una ventana con una barra superior:
 * la barra se arrastra para mover la ventana (clic derecho la vuelve al centro)
 * y, si el servidor tiene el mod, muestra una pestaña por cada contenedor cercano.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
	@Unique private static final int TAB_W = 22;
	@Unique private static final int TAB_H = 20;
	@Unique private static final int GRIP_H = 10;

	@Shadow @Final protected int imageWidth;
	@Shadow @Final protected int imageHeight;
	@Shadow @Final protected AbstractContainerMenu menu;
	@Shadow protected int leftPos;
	@Shadow protected int topPos;

	@Unique private boolean cosmostabs$dragging;
	@Unique private double cosmostabs$grabX;
	@Unique private double cosmostabs$grabY;

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
	private void cosmostabs$applyOffset(CallbackInfo ci) {
		if (!this.cosmostabs$enabled()) {
			return;
		}
		int[] offset = WindowPositions.get(this.cosmostabs$key());
		this.cosmostabs$moveTo(this.leftPos + offset[0], this.topPos + offset[1], false);
	}

	/** Mueve la ventana (y los botones de la pantalla) a la posicion pedida, sin salirse de la pantalla. */
	@Unique
	private void cosmostabs$moveTo(int x, int y, boolean moveWidgets) {
		Screen screen = this.cosmostabs$screen();
		x = Math.max(0, Math.min(x, screen.width - this.imageWidth));
		y = Math.max(TAB_H, Math.min(y, screen.height - this.imageHeight));
		int dx = x - this.leftPos;
		int dy = y - this.topPos;
		this.leftPos = x;
		this.topPos = y;
		if (moveWidgets && (dx != 0 || dy != 0)) {
			for (GuiEventListener child : screen.children()) {
				if (child instanceof AbstractWidget widget) {
					widget.setX(widget.getX() + dx);
					widget.setY(widget.getY() + dy);
				}
			}
		}
	}

	@Unique
	private void cosmostabs$saveOffset() {
		Screen screen = this.cosmostabs$screen();
		WindowPositions.set(this.cosmostabs$key(),
				this.leftPos - (screen.width - this.imageWidth) / 2,
				this.topPos - (screen.height - this.imageHeight) / 2);
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
			Component name = tabs.get(hovered).name();
			graphics.setTooltipForNextFrame(font, name, mouseX, mouseY);
		} else if (gripHover && !this.cosmostabs$dragging) {
			graphics.setTooltipForNextFrame(font,
					Component.literal("Arrastra para mover · clic derecho: centrar"), mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$clickBar(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		if (!this.cosmostabs$enabled() || !this.cosmostabs$inBar(event.x(), event.y())) {
			return;
		}
		cir.setReturnValue(true);
		int tab = this.cosmostabs$tabAt(event.x(), event.y());
		if (tab >= 0) {
			TabsPayload.Tab target = this.cosmostabs$tabs().get(tab);
			if (event.button() == 0 && !target.current() && this.menu.getCarried().isEmpty()) {
				ClientPlayNetworking.send(new SwitchTabPayload(target.pos()));
			}
		} else if (event.button() == 1) {
			Screen screen = this.cosmostabs$screen();
			this.cosmostabs$moveTo((screen.width - this.imageWidth) / 2, (screen.height - this.imageHeight) / 2, true);
			this.cosmostabs$saveOffset();
		} else if (event.button() == 0) {
			this.cosmostabs$dragging = true;
			this.cosmostabs$grabX = event.x() - this.leftPos;
			this.cosmostabs$grabY = event.y() - this.topPos;
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$dragWindow(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (this.cosmostabs$dragging) {
			this.cosmostabs$moveTo((int) Math.round(event.x() - this.cosmostabs$grabX),
					(int) Math.round(event.y() - this.cosmostabs$grabY), true);
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
