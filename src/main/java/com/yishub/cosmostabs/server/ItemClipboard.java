package com.yishub.cosmostabs.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ctrl+X / Ctrl+V (cortar y pegar) entre contenedores del mismo grupo. "Cortar" recuerda que ranuras marcaste;
 * "pegar" las mueve al contenedor abierto (mover, no duplicar). Todo se valida en el servidor.
 */
public final class ItemClipboard {
	private record Clip(ResourceKey<Level> dimension, BlockPos source, List<Integer> slots) {}

	private static final Map<UUID, Clip> CLIPS = new HashMap<>();

	private ItemClipboard() {}

	/** Contenedor abierto en la ventana principal o en la separada. */
	private static BlockPos open(ServerPlayer player, boolean side) {
		return side ? ContainerGroups.side(player) : ContainerGroups.current(player);
	}

	public static void cut(ServerPlayer player, List<Integer> slots, boolean side) {
		ServerLevel level = player.level();
		BlockPos source = open(player, side);
		Container container = source == null ? null : containerAt(player, level, source);
		if (container == null || slots.isEmpty()) {
			return;
		}
		List<Integer> valid = slots.stream().distinct().filter(i -> i >= 0 && i < container.getContainerSize()).toList();
		CLIPS.put(player.getUUID(), new Clip(level.dimension(), source, valid));
		player.sendOverlayMessage(Component.literal(
				"Cortadas " + valid.size() + " ranura(s) · Ctrl+V en otro contenedor pegado para moverlas"));
	}

	public static void paste(ServerPlayer player, boolean side) {
		ServerLevel level = player.level();
		Clip clip = CLIPS.get(player.getUUID());
		BlockPos target = open(player, side);
		if (clip == null || target == null) {
			return;
		}
		if (clip.dimension() != level.dimension() || clip.source().equals(target)) {
			player.sendOverlayMessage(Component.literal("Abre otro contenedor del grupo para pegar"));
			return;
		}
		if (!ContainerGroups.canReach(player, level, clip.source()) || !ContainerGroups.canReach(player, level, target)
				|| !ContainerGroups.group(level, target).contains(clip.source())) {
			player.sendOverlayMessage(Component.literal("Solo se puede mover entre contenedores pegados y a tu alcance"));
			return;
		}
		Container from = containerAt(player, level, clip.source());
		Container to = containerAt(player, level, target);
		if (from == null || to == null) {
			return;
		}
		boolean toShulker = level.getBlockEntity(target) instanceof ShulkerBoxBlockEntity;
		int movedItems = 0;
		int leftStacks = 0;
		for (int slot : clip.slots()) {
			if (slot >= from.getContainerSize()) {
				continue;
			}
			ItemStack stack = from.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			int before = stack.getCount();
			ItemStack rest = insert(to, stack.copy(), toShulker);
			from.setItem(slot, rest);
			movedItems += before - rest.getCount();
			if (!rest.isEmpty()) {
				leftStacks++;
			}
		}
		from.setChanged();
		to.setChanged();
		CLIPS.remove(player.getUUID());
		String message = "Movidos " + movedItems + " objeto(s)";
		if (leftStacks > 0) {
			message += " · no cabia todo: " + leftStacks + " pila(s) siguen en el origen";
		}
		player.sendOverlayMessage(Component.literal(message));
	}

	public static void forget(ServerPlayer player) {
		CLIPS.remove(player.getUUID());
	}

	/** Mete lo que pueda de {@code stack} en {@code to}: primero completa pilas iguales, luego huecos. Devuelve lo que sobra. */
	private static ItemStack insert(Container to, ItemStack stack, boolean toShulker) {
		if (toShulker && !stack.getItem().canFitInsideContainerItems()) {
			return stack;
		}
		for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
			for (int i = 0; i < to.getContainerSize() && !stack.isEmpty(); i++) {
				ItemStack there = to.getItem(i);
				if (!to.canPlaceItem(i, stack)) {
					continue;
				}
				int max = Math.min(to.getMaxStackSize(stack), stack.getMaxStackSize());
				if (pass == 0 && !there.isEmpty() && ItemStack.isSameItemSameComponents(there, stack)) {
					int n = Math.min(stack.getCount(), max - there.getCount());
					if (n > 0) {
						there.grow(n);
						stack.shrink(n);
					}
				} else if (pass == 1 && there.isEmpty()) {
					to.setItem(i, stack.split(Math.min(stack.getCount(), max)));
				}
			}
		}
		return stack;
	}

	/** El inventario detras de un bloque, igual que lo ve su menu (cofre doble completo, cofre de Ender del jugador). */
	static Container containerAt(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!ContainerGroups.isStorage(level, pos)) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		BlockEntity be = level.getBlockEntity(pos);
		Container container;
		if (state.getBlock() instanceof ChestBlock chest) {
			container = ChestBlock.getContainer(chest, state, level, pos, true);
		} else if (be instanceof EnderChestBlockEntity) {
			container = player.getEnderChestInventory();
		} else {
			container = be instanceof Container c ? c : null;
		}
		if (be instanceof RandomizableContainer randomizable) {
			randomizable.unpackLootTable(player);
		}
		return container;
	}
}
