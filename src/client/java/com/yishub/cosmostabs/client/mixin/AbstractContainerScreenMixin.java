package com.yishub.cosmostabs.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.yishub.cosmostabs.CosmosTabs;
import com.yishub.cosmostabs.client.CosmosTabsClient;
import com.yishub.cosmostabs.client.MovableWindow;
import com.yishub.cosmostabs.client.WindowPositions;
import com.yishub.cosmostabs.net.SwitchTabPayload;
import com.yishub.cosmostabs.net.TabsPayload;
import com.yishub.cosmostabs.client.DualContainerScreen;
import com.yishub.cosmostabs.menu.DualContainerMenu;
import com.yishub.cosmostabs.net.CutItemsPayload;
import com.yishub.cosmostabs.net.DetachTabPayload;
import com.yishub.cosmostabs.net.PasteItemsPayload;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
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
	@Unique private double cosmostabs$grabRealX;
	@Unique private double cosmostabs$grabRealY;
	@Unique private double cosmostabs$grabOffsetX;
	@Unique private double cosmostabs$grabOffsetY;
	/** Ranuras marcadas con Ctrl+clic (indices de ranura del menu). */
	@Unique private final Set<Integer> cosmostabs$selected = new LinkedHashSet<>();
	/** Ranuras cortadas con Ctrl+X en esta ventana, pendientes de pegar. */
	@Unique private final Set<Integer> cosmostabs$cut = new LinkedHashSet<>();
	/** Pestaña pulsada (aun sin soltar) y si ya se esta arrastrando para separarla. */
	@Unique private int cosmostabs$pressedTab = -1;
	@Unique private boolean cosmostabs$tabDragging;
	@Unique private double cosmostabs$pressX;
	@Unique private double cosmostabs$pressY;

	@Shadow
	protected abstract boolean isHovering(int left, int top, int w, int h, double xm, double ym);

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

	/** Ctrl+X / Ctrl+V solo en ventanas de almacenamiento y si el servidor tiene el mod. */
	@Unique
	private boolean cosmostabs$clipboardEnabled() {
		AbstractContainerMenu m = this.menu;
		return this.cosmostabs$enabled()
				&& (m instanceof ChestMenu || m instanceof ShulkerBoxMenu || m instanceof DispenserMenu || m instanceof HopperMenu
						|| m instanceof DualContainerMenu)
				&& ClientPlayNetworking.canSend(CutItemsPayload.TYPE);
	}

	/**
	 * Ctrl pulsado: segun el evento o segun el estado actual del teclado (algunos origenes de
	 * eventos, como la prueba automatica, no rellenan los modificadores).
	 */
	@Unique
	private static boolean cosmostabs$ctrl(InputWithModifiers event) {
		return event.hasControlDownWithQuirk()
				|| InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
	}

	/** Ctrl + letra (segun la distribucion del teclado), sin Shift ni Alt. */
	@Unique
	private static boolean cosmostabs$shortcut(KeyEvent event, char letter) {
		return event.shortcutKey() == letter && cosmostabs$ctrl(event) && !event.hasShiftDown() && !event.hasAltDown();
	}

	/** Ranura del contenedor (no del inventario del jugador) bajo el raton, o null. */
	@Unique
	private Slot cosmostabs$containerSlotAt(double mx, double my) {
		for (Slot slot : this.menu.slots) {
			if (!(slot.container instanceof Inventory) && slot.isActive()
					&& this.isHovering(slot.x, slot.y, 16, 16, mx, my)) {
				return slot;
			}
		}
		return null;
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

		// Ranuras marcadas con Ctrl+clic.
		for (int index : this.cosmostabs$selected) {
			if (index < this.menu.slots.size()) {
				Slot slot = this.menu.slots.get(index);
				int x = this.leftPos + slot.x;
				int y = this.topPos + slot.y;
				graphics.fill(x, y, x + 16, y + 16, 0x704080FF);
				graphics.outline(x - 1, y - 1, 18, 18, 0xFF4080FF);
			}
		}
		// Ranuras cortadas con Ctrl+X que aun no se pegaron: atenuadas y con borde punteado.
		for (int index : this.cosmostabs$cut) {
			if (index < this.menu.slots.size()) {
				Slot slot = this.menu.slots.get(index);
				int x = this.leftPos + slot.x;
				int y = this.topPos + slot.y;
				graphics.fill(x, y, x + 16, y + 16, 0x90C6C6C6);
				for (int d = 0; d < 18; d += 4) {
					graphics.fill(x - 1 + d, y - 1, x + 1 + d, y, 0xFF4080FF);
					graphics.fill(x - 1 + d, y + 16, x + 1 + d, y + 17, 0xFF4080FF);
					graphics.fill(x - 1, y - 1 + d, x, y + 1 + d, 0xFF4080FF);
					graphics.fill(x + 16, y - 1 + d, x + 17, y + 1 + d, 0xFF4080FF);
				}
			}
		}

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
			// Actual: del color de la ventana. Separada: azulada. Resto: gris (mas claro con el raton encima).
			int bg = tab.current() ? 0xFFC6C6C6 : tab.detached() ? 0xFF7A8FB0 : (i == hovered ? 0xFFA8A8A8 : 0xFF8B8B8B);
			graphics.fill(x, y, x + TAB_W, this.topPos + (tab.current() ? 1 : 0), bg);
			graphics.fill(x, y, x + TAB_W, y + 1, 0xFFFFFFFF);
			graphics.fill(x, y, x + 1, this.topPos, 0xFFFFFFFF);
			graphics.fill(x + TAB_W - 1, y, x + TAB_W, this.topPos, 0xFF373737);
			graphics.item(tab.icon(), x + 3, y + 2);
		}
		if (this.cosmostabs$tabDragging && this.cosmostabs$pressedTab < tabs.size()) {
			// Pestaña "fantasma" que sigue al raton mientras la arrastras fuera para separarla.
			int gx = mouseX - TAB_W / 2;
			int gy = mouseY - TAB_H / 2;
			graphics.fill(gx, gy, gx + TAB_W, gy + TAB_H, 0xC0C6C6C6);
			graphics.outline(gx, gy, TAB_W, TAB_H, 0xFF4080FF);
			graphics.item(tabs.get(this.cosmostabs$pressedTab).icon(), gx + 3, gy + 2);
		} else if (hovered >= 0) {
			TabsPayload.Tab tab = tabs.get(hovered);
			graphics.setTooltipForNextFrame(font, tab.current() || tab.detached() ? tab.name()
					: tab.name().copy().append(Component.literal(" · arrastra afuera para abrir al lado")), mouseX, mouseY);
		} else if (gripHover && !this.cosmostabs$dragging) {
			graphics.setTooltipForNextFrame(font,
					Component.literal(this.cosmostabs$clipboardEnabled()
							? "Arrastra: mover · clic der.: centrar · Ctrl+clic, Ctrl+X y Ctrl+V: pasar objetos"
							: "Arrastra para mover · clic derecho: centrar"), mouseX, mouseY);
		}
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$clickBar(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		CosmosTabs.debug("clic ({}, {}) boton={} desplazamiento=({}, {}) barra={} pestaña={}", event.x(), event.y(),
				event.button(), this.cosmostabs$offsetX(), this.cosmostabs$offsetY(),
				this.cosmostabs$inBar(event.x(), event.y()), this.cosmostabs$tabAt(event.x(), event.y()));
		if (cosmostabs$ctrl(event) && event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.cosmostabs$clipboardEnabled()
				&& this.menu.getCarried().isEmpty()) {
			Slot slot = this.cosmostabs$containerSlotAt(event.x(), event.y());
			if (slot != null) {
				if (!this.cosmostabs$selected.remove(slot.index) && slot.hasItem()) {
					this.cosmostabs$selected.add(slot.index);
				}
				cir.setReturnValue(true);
				return;
			}
		}
		if (!this.cosmostabs$enabled() || !this.cosmostabs$inBar(event.x(), event.y())) {
			return;
		}
		cir.setReturnValue(true);
		int tab = this.cosmostabs$tabAt(event.x(), event.y());
		if (tab >= 0) {
			// Se decide al soltar: clic = cambiar de pestaña, arrastrar afuera = abrir en otra ventana.
			if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.menu.getCarried().isEmpty()) {
				this.cosmostabs$pressedTab = tab;
				this.cosmostabs$pressX = event.x();
				this.cosmostabs$pressY = event.y();
			}
		} else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			this.cosmostabs$setOffset(0, 0);
			this.cosmostabs$saveOffset();
		} else if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			this.cosmostabs$dragging = true;
			// Punto agarrado en coordenadas reales de pantalla (las del evento vienen sin el desplazamiento).
			this.cosmostabs$grabRealX = event.x() + this.cosmostabs$offsetX;
			this.cosmostabs$grabRealY = event.y() + this.cosmostabs$offsetY;
			this.cosmostabs$grabOffsetX = this.cosmostabs$offsetX;
			this.cosmostabs$grabOffsetY = this.cosmostabs$offsetY;
		}
	}

	/** Soltar una pestaña pulsada: sin moverla = cambiar a ella; arrastrada fuera de la barra = abrirla al lado. */
	@Unique
	private void cosmostabs$releaseTab(double mx, double my) {
		List<TabsPayload.Tab> tabs = this.cosmostabs$tabs();
		int index = this.cosmostabs$pressedTab;
		boolean dragged = this.cosmostabs$tabDragging;
		this.cosmostabs$pressedTab = -1;
		this.cosmostabs$tabDragging = false;
		if (index >= tabs.size() || !this.menu.getCarried().isEmpty()) {
			return;
		}
		TabsPayload.Tab target = tabs.get(index);
		if (!dragged) {
			if (!target.current()) {
				CosmosTabs.debug("enviando cambio a {}", target.pos().toShortString());
				CosmosTabsClient.markSwitchPending();
				ClientPlayNetworking.send(new SwitchTabPayload(target.pos()));
			}
		} else if (!this.cosmostabs$inBar(mx, my) && !target.current() && ClientPlayNetworking.canSend(DetachTabPayload.TYPE)) {
			// La ventana nueva aparece donde soltaste la pestaña (su barra bajo el raton).
			CosmosTabsClient.setSidePanelPosition((int) mx - this.leftPos - TAB_W, (int) my - this.topPos + GRIP_H / 2);
			CosmosTabsClient.markSwitchPending();
			ClientPlayNetworking.send(new DetachTabPayload(target.pos()));
		}
	}

	/** Ctrl+A marca todo, Ctrl+X corta lo marcado, Ctrl+V lo mueve a este contenedor. */
	@Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$clipboardKeys(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (!this.cosmostabs$clipboardEnabled()) {
			return;
		}
		if (cosmostabs$shortcut(event, 'a')) {
			this.cosmostabs$selected.clear();
			for (Slot slot : this.menu.slots) {
				if (!(slot.container instanceof Inventory) && slot.hasItem()) {
					this.cosmostabs$selected.add(slot.index);
				}
			}
			cir.setReturnValue(true);
		} else if (cosmostabs$shortcut(event, 'x')) {
			// Con dos ventanas, se corta de la ventana de la primera ranura marcada que tenga algo.
			List<Integer> slots = new ArrayList<>();
			Boolean side = null;
			this.cosmostabs$cut.clear();
			for (int index : this.cosmostabs$selected) {
				Slot slot = this.menu.slots.get(index);
				if (!slot.hasItem()) {
					continue;
				}
				boolean slotSide = this.menu instanceof DualContainerMenu dual && dual.isSideSlot(slot);
				if (side == null) {
					side = slotSide;
				}
				if (slotSide == side) {
					slots.add(slot.getContainerSlot());
					this.cosmostabs$cut.add(index);
				}
			}
			if (!slots.isEmpty()) {
				ClientPlayNetworking.send(new CutItemsPayload(slots, side));
			}
			this.cosmostabs$selected.clear();
			cir.setReturnValue(true);
		} else if (cosmostabs$shortcut(event, 'c') && !this.cosmostabs$selected.isEmpty()) {
			// Copiar no existe (duplicaria objetos): avisar en vez de no hacer nada.
			Minecraft.getInstance().gui.hud.setOverlayMessage(Component.literal("Para mover objetos usa Ctrl+X (cortar) y Ctrl+V"), false);
			cir.setReturnValue(true);
		} else if (cosmostabs$shortcut(event, 'v')) {
			this.cosmostabs$cut.clear();
			// Con dos ventanas, se pega en la que esta bajo el raton.
			Minecraft mc = Minecraft.getInstance();
			double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
			double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
			boolean side = (Object) this instanceof DualContainerScreen dual && dual.isOverSide(mx, my);
			ClientPlayNetworking.send(new PasteItemsPayload(side));
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$dragWindow(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
		if (this.cosmostabs$dragging) {
			// Posicion absoluta: la ventana sigue al raton aunque se pierda o junte algun evento de movimiento.
			double realX = event.x() + this.cosmostabs$offsetX;
			double realY = event.y() + this.cosmostabs$offsetY;
			this.cosmostabs$setOffset(this.cosmostabs$grabOffsetX + realX - this.cosmostabs$grabRealX,
					this.cosmostabs$grabOffsetY + realY - this.cosmostabs$grabRealY);
			cir.setReturnValue(true);
		} else if (this.cosmostabs$pressedTab >= 0) {
			if (Math.abs(event.x() - this.cosmostabs$pressX) + Math.abs(event.y() - this.cosmostabs$pressY) > 4) {
				this.cosmostabs$tabDragging = true;
			}
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
	private void cosmostabs$dropWindow(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
		if (this.cosmostabs$dragging) {
			this.cosmostabs$dragging = false;
			this.cosmostabs$saveOffset();
			cir.setReturnValue(true);
		} else if (this.cosmostabs$pressedTab >= 0) {
			this.cosmostabs$releaseTab(event.x(), event.y());
			cir.setReturnValue(true);
		} else if (this.cosmostabs$enabled() && this.cosmostabs$inBar(event.x(), event.y())) {
			// Que soltar el clic sobre la barra no cuente como "clic fuera" (tiraria el objeto que llevas).
			cir.setReturnValue(true);
		}
	}
}
