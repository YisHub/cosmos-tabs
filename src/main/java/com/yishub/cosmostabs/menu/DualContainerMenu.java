package com.yishub.cosmostabs.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Dos contenedores a la vez: el principal (con el inventario del jugador debajo, como un cofre
 * normal) y otro "separado" que el cliente dibuja en una segunda ventana flotante.
 *
 * <p>Orden de ranuras: [principal][inventario del jugador (36)][separado].
 */
public class DualContainerMenu extends AbstractContainerMenu {
	/** Lo que el cliente necesita saber al abrirlo: tamaños y nombre del contenedor separado. */
	public record Data(int mainSize, int sideSize, Component sideTitle) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Data::mainSize,
				ByteBufCodecs.VAR_INT, Data::sideSize,
				ComponentSerialization.STREAM_CODEC, Data::sideTitle,
				Data::new);
	}

	/** Donde empieza la ventana separada respecto a la principal (el cliente la mueve despues). */
	public static final int SIDE_DEFAULT_X = 176 + 8;
	public static final int SIDE_DEFAULT_Y = 0;

	private final Container main;
	private final Container side;
	private final Component sideTitle;
	private final int sideStart;

	/** Cliente: los contenidos llegan por la red. */
	public DualContainerMenu(int containerId, Inventory inventory, Data data) {
		this(containerId, inventory, new SimpleContainer(data.mainSize()), new SimpleContainer(data.sideSize()), data.sideTitle());
	}

	/** Servidor: los contenedores reales. */
	public DualContainerMenu(int containerId, Inventory inventory, Container main, Container side, Component sideTitle) {
		super(CosmosMenus.DUAL, containerId);
		this.main = main;
		this.side = side;
		this.sideTitle = sideTitle;
		main.startOpen(inventory.player);
		side.startOpen(inventory.player);

		addGrid(main, 8, 18);
		this.addStandardInventorySlots(inventory, 8, 18 + rows(main.getContainerSize()) * 18 + 13);
		this.sideStart = this.slots.size();
		addGrid(side, SIDE_DEFAULT_X + 8, SIDE_DEFAULT_Y + 18);
	}

	public static int rows(int size) {
		return (size + 8) / 9;
	}

	private void addGrid(Container container, int left, int top) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			this.addSlot(new Slot(container, i, left + (i % 9) * 18, top + (i / 9) * 18));
		}
	}

	public int mainRows() {
		return rows(this.main.getContainerSize());
	}

	public int sideRows() {
		return rows(this.side.getContainerSize());
	}

	public int mainSize() {
		return this.main.getContainerSize();
	}

	public int sideSize() {
		return this.side.getContainerSize();
	}

	public Component sideTitle() {
		return this.sideTitle;
	}

	public boolean isSideSlot(Slot slot) {
		return slot.index >= this.sideStart;
	}

	public int sideStart() {
		return this.sideStart;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.main.stillValid(player) && this.side.stillValid(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		this.main.stopOpen(player);
		this.side.stopOpen(player);
	}

	/** Shift+clic: de cualquiera de los dos contenedores al inventario, y del inventario al principal. */
	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		Slot slot = this.slots.get(slotIndex);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack clicked = stack.copy();
		int mainEnd = this.main.getContainerSize();
		int inventoryEnd = this.sideStart;
		boolean moved;
		if (slotIndex < mainEnd || slotIndex >= inventoryEnd) {
			moved = this.moveItemStackTo(stack, mainEnd, inventoryEnd, true);
		} else {
			moved = this.moveItemStackTo(stack, 0, mainEnd, false);
		}
		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return clicked;
	}
}
