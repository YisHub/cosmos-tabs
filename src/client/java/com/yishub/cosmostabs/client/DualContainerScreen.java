package com.yishub.cosmostabs.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yishub.cosmostabs.client.mixin.SlotAccessor;
import com.yishub.cosmostabs.menu.DualContainerMenu;
import com.yishub.cosmostabs.net.AttachTabPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Pantalla del menu doble: la ventana principal (como un cofre normal, con inventario y pestañas)
 * y una segunda ventana flotante con el contenedor separado. La segunda se arrastra por su barra;
 * soltarla sobre la barra de pestañas (o pulsar la X) la vuelve a juntar.
 *
 * <p>Todas las posiciones de la ventana separada son relativas a la principal (leftPos/topPos), asi
 * cuando se mueve la principal se mueven las dos.
 */
public class DualContainerScreen extends AbstractContainerScreen<DualContainerMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
	private static final int PANEL_W = 176;
	private static final int HEADER_H = 10;
	private static final int TAB_BAR_H = 20;

	private int sideX;
	private int sideY;
	private boolean draggingSide;

	public DualContainerScreen(DualContainerMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title, PANEL_W, 114 + menu.mainRows() * 18);
		this.inventoryLabelY = this.imageHeight - 94;
		int[] pos = CosmosTabsClient.sidePanelPosition();
		this.sideX = pos[0];
		this.sideY = pos[1];
	}

	@Override
	protected void init() {
		super.init();
		this.placeSide(this.sideX, this.sideY);
	}

	private int sideHeight() {
		return this.menu.sideRows() * 18 + 17 + 7;
	}

	/** Coloca la ventana separada (y sus ranuras) sin que se salga de la pantalla. */
	private void placeSide(int x, int y) {
		int[] offset = this instanceof MovableWindow w ? new int[] {w.cosmostabs$offsetX(), w.cosmostabs$offsetY()} : new int[2];
		int minX = -this.leftPos - offset[0];
		int maxX = this.width - PANEL_W - this.leftPos - offset[0];
		int minY = HEADER_H - this.topPos - offset[1];
		int maxY = this.height - this.sideHeight() - this.topPos - offset[1];
		this.sideX = Math.max(minX, Math.min(x, maxX));
		this.sideY = Math.max(minY, Math.min(y, maxY));
		int start = this.menu.sideStart();
		for (int i = 0; i < this.menu.sideSize(); i++) {
			Slot slot = this.menu.slots.get(start + i);
			((SlotAccessor) slot).cosmostabs$setX(this.sideX + 8 + (i % 9) * 18);
			((SlotAccessor) slot).cosmostabs$setY(this.sideY + 18 + (i / 9) * 18);
		}
	}

	/** El raton (coordenadas de pantalla corregidas) esta sobre la ventana separada o su barra. */
	public boolean isOverSide(double mx, double my) {
		int x = this.leftPos + this.sideX;
		int y = this.topPos + this.sideY;
		return mx >= x && mx < x + PANEL_W && my >= y - HEADER_H && my < y + this.sideHeight();
	}

	private boolean isOverSideHeader(double mx, double my) {
		int x = this.leftPos + this.sideX;
		int y = this.topPos + this.sideY - HEADER_H;
		return mx >= x && mx < x + PANEL_W && my >= y && my < y + HEADER_H;
	}

	private boolean isOverCloseButton(double mx, double my) {
		int x = this.leftPos + this.sideX + PANEL_W - HEADER_H;
		int y = this.topPos + this.sideY - HEADER_H;
		return mx >= x && mx < x + HEADER_H && my >= y && my < y + HEADER_H;
	}

	private boolean isOverMainTabBar(double mx, double my) {
		return mx >= this.leftPos && mx < this.leftPos + this.imageWidth && my >= this.topPos - TAB_BAR_H && my < this.topPos;
	}

	private void attach() {
		CosmosTabsClient.markSwitchPending();
		ClientPlayNetworking.send(new AttachTabPayload());
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.isOverSideHeader(event.x(), event.y())) {
			if (this.isOverCloseButton(event.x(), event.y())) {
				this.attach();
			} else {
				this.draggingSide = true;
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (this.draggingSide) {
			// Deltas acumulados en doble para no perder fracciones con escalas de interfaz grandes.
			this.dragRestX += dx;
			this.dragRestY += dy;
			int stepX = (int) this.dragRestX;
			int stepY = (int) this.dragRestY;
			this.dragRestX -= stepX;
			this.dragRestY -= stepY;
			this.placeSide(this.sideX + stepX, this.sideY + stepY);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	private double dragRestX;
	private double dragRestY;

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.draggingSide) {
			this.draggingSide = false;
			if (this.isOverMainTabBar(event.x(), event.y())) {
				this.attach();
			} else {
				CosmosTabsClient.setSidePanelPosition(this.sideX, this.sideY);
			}
			return true;
		}
		return super.mouseReleased(event);
	}

	/** Un clic dentro de la ventana separada no es "fuera" (si no, soltaria el objeto que llevas). */
	@Override
	protected boolean hasClickedOutside(double mx, double my, int xo, int yo) {
		return super.hasClickedOutside(mx, my, xo, yo) && !this.isOverSide(mx, my);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int mainRows = this.menu.mainRows();
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, PANEL_W, mainRows * 18 + 17, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos + mainRows * 18 + 17, 0.0F, 126.0F, PANEL_W, 96, 256, 256);
		this.coverMissingSlots(graphics, this.leftPos + 8, this.topPos + 18, this.menu.mainSize(), mainRows);

		int x = this.leftPos + this.sideX;
		int y = this.topPos + this.sideY;
		int sideRows = this.menu.sideRows();
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, PANEL_W, sideRows * 18 + 17, 256, 256);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y + sideRows * 18 + 17, 0.0F, 215.0F, PANEL_W, 7, 256, 256);
		this.coverMissingSlots(graphics, x + 8, y + 18, this.menu.sideSize(), sideRows);

		// Barra de la ventana separada: arrastrar para moverla, X (o soltarla en las pestañas) para juntarla.
		int hy = y - HEADER_H;
		boolean hover = this.draggingSide || this.isOverSideHeader(mouseX, mouseY);
		graphics.fill(x, hy, x + PANEL_W, y, hover ? 0xFF6F6F6F : 0xFF555555);
		graphics.outline(x, hy, PANEL_W, HEADER_H, 0xFF373737);
		int cx = x + (PANEL_W - HEADER_H) / 2;
		for (int i = -2; i <= 2; i++) {
			graphics.fill(cx + i * 4, hy + 4, cx + i * 4 + 2, hy + 6, 0xFFC6C6C6);
		}
		int bx = x + PANEL_W - HEADER_H;
		boolean closeHover = this.isOverCloseButton(mouseX, mouseY);
		graphics.fill(bx, hy, bx + HEADER_H, y, closeHover ? 0xFFB04040 : 0xFF7A3030);
		graphics.text(this.font, "x", bx + 3, hy + 1, 0xFFFFFFFF, false);
		if (this.draggingSide && this.isOverMainTabBar(mouseX, mouseY)) {
			graphics.fill(this.leftPos, this.topPos - TAB_BAR_H, this.leftPos + this.imageWidth, this.topPos, 0x5540A0FF);
		}
	}

	/** Si el contenedor no llena la ultima fila (tolva, dispensador...), tapa los huecos de la textura. */
	private void coverMissingSlots(GuiGraphicsExtractor graphics, int left, int top, int size, int rows) {
		for (int i = size; i < rows * 9; i++) {
			int sx = left + (i % 9) * 18;
			int sy = top + (i / 9) * 18;
			graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFC6C6C6);
		}
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		graphics.text(this.font, this.menu.sideTitle(), this.sideX + 8, this.sideY + 6, 0xFF404040, false);
	}
}
