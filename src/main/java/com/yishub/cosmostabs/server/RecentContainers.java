package com.yishub.cosmostabs.server;

import com.yishub.cosmostabs.net.TabsPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Recuerda, por jugador, los ultimos contenedores que abrio a mano. Solo se ofrecen como
 * pestañas los que siguen dentro del margen que vanilla ya tolera para mantener un menu
 * abierto (alcance de interaccion + 4 bloques, ver Container.stillValidBlockEntity).
 */
public final class RecentContainers {
	private static final int MAX_TABS = 9;
	private static final double RANGE_BUFFER = 4.0;

	private record Entry(ResourceKey<Level> dimension, LinkedHashSet<BlockPos> positions) {}

	private static final Map<UUID, Entry> BY_PLAYER = new HashMap<>();

	private RecentContainers() {}

	/** Llamado cuando el jugador abre un menu haciendo clic en el bloque {@code pos}. */
	public static void opened(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		pos = canonical(level, pos);
		Entry entry = BY_PLAYER.get(player.getUUID());
		if (entry == null || entry.dimension() != level.dimension()) {
			entry = new Entry(level.dimension(), new LinkedHashSet<>());
			BY_PLAYER.put(player.getUUID(), entry);
		}
		entry.positions().add(pos);
		while (entry.positions().size() > MAX_TABS) {
			entry.positions().remove(entry.positions().iterator().next());
		}
		sendTabs(player, pos);
	}

	/** Pestaña pulsada: abre el contenedor igual que si el jugador le hiciera clic con la mano vacia. */
	public static void switchTo(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		Entry entry = BY_PLAYER.get(player.getUUID());
		if (entry == null || entry.dimension() != level.dimension() || !entry.positions().contains(pos)) {
			return;
		}
		if (!isAvailable(player, level, pos) || !player.containerMenu.getCarried().isEmpty()) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		if (state.useWithoutItem(level, player, hit).consumesAction()) {
			sendTabs(player, pos);
		}
	}

	public static void forget(ServerPlayer player) {
		BY_PLAYER.remove(player.getUUID());
	}

	private static void sendTabs(ServerPlayer player, BlockPos current) {
		if (!ServerPlayNetworking.canSend(player, TabsPayload.TYPE)) {
			return;
		}
		ServerLevel level = player.level();
		List<TabsPayload.Tab> tabs = new ArrayList<>();
		for (BlockPos pos : BY_PLAYER.get(player.getUUID()).positions()) {
			if (!isAvailable(player, level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			Component name = level.getBlockEntity(pos) instanceof Nameable nameable
					? nameable.getDisplayName()
					: state.getBlock().getName();
			tabs.add(new TabsPayload.Tab(pos, state.getBlock().asItem().getDefaultInstance(), name, pos.equals(current)));
		}
		ServerPlayNetworking.send(player, new TabsPayload(tabs));
	}

	private static boolean isAvailable(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || !player.isWithinBlockInteractionRange(pos, RANGE_BUFFER)) {
			return false;
		}
		MenuProvider provider = level.getBlockState(pos).getMenuProvider(level, pos);
		return provider != null;
	}

	/** Las dos mitades de un cofre doble son el mismo contenedor: se guarda siempre la misma. */
	private static BlockPos canonical(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			return other.asLong() < pos.asLong() ? other : pos;
		}
		return pos.immutable();
	}
}
