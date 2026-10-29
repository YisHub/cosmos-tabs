package com.yishub.cosmostabs.server;

import com.yishub.cosmostabs.CosmosTabs;
import com.yishub.cosmostabs.menu.DualContainerMenu;
import com.yishub.cosmostabs.net.TabsPayload;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.yishub.cosmostabs.net.DualOpenPayload;
import net.minecraft.world.MenuProvider;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Las pestañas de un contenedor son los contenedores de almacenamiento conectados a el, pegados
 * cara con cara (en cadena). Asi solo se salta entre cofres de una misma pared o grupo, nunca a
 * uno que esta en otra parte de la habitacion. Una pestaña se puede "separar" para ver dos
 * contenedores del grupo a la vez (DualContainerMenu).
 */
public final class ContainerGroups {
	/** Maximo de contenedores (pestañas) de un grupo; el cliente los reparte en paginas. */
	private static final int MAX_TABS = 64;
	/**
	 * Tope de bloques a recorrer al buscar el grupo, para que una pared enorme no cueste de mas.
	 * Holgado respecto a MAX_TABS porque un cofre doble son dos bloques.
	 */
	private static final int MAX_SCAN = 4 * MAX_TABS;
	private static final double RANGE_BUFFER = 4.0;

	/** Contenedor principal que cada jugador tiene abierto (forma canonica). */
	private static final Map<UUID, BlockPos> CURRENT = new HashMap<>();
	/** Contenedor separado en la segunda ventana, si hay. */
	private static final Map<UUID, BlockPos> SIDE = new HashMap<>();

	private ContainerGroups() {}

	/**
	 * Unica regla para saber si un bloque cuenta: almacenamiento (cofres, barriles, shulkers,
	 * dispensadores, tolvas, cofres de Ender y cofres de otros mods que extienden la clase vanilla).
	 * Hornos, mesas de trabajo y el Crafter quedan fuera: otros mods como Cinematic Interactions
	 * les ponen su propia pantalla y abrirlos desde una pestaña se la saltaria.
	 */
	public static boolean isStorage(Level level, BlockPos pos) {
		BlockEntity be = level.getBlockEntity(pos);
		return be instanceof EnderChestBlockEntity
				|| (be instanceof RandomizableContainerBlockEntity && !(be instanceof CrafterBlockEntity));
	}

	/** Llamado cuando el jugador abre un menu haciendo clic en el bloque {@code pos}. */
	public static void opened(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		SIDE.remove(player.getUUID());
		if (!isStorage(level, pos)) {
			CURRENT.remove(player.getUUID());
			return;
		}
		pos = canonical(level, pos);
		CURRENT.put(player.getUUID(), pos);
		sendTabs(player, pos);
	}

	/**
	 * Pestaña pulsada: pasa a ese contenedor. Si habia una ventana separada se mantiene (salvo que
	 * la pestaña pulsada sea justo la separada, que entonces pasa a ser la unica).
	 */
	public static void switchTo(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		BlockPos current = current(player);
		CosmosTabs.debug("switchTo {} pide {} (abierto {})", player.getName().getString(), pos.toShortString(),
				current == null ? "-" : current.toShortString());
		if (!inGroupAndReachable(player, level, current, pos)) {
			return;
		}
		BlockPos side = side(player);
		if (side != null && !side.equals(pos)) {
			openDual(player, level, pos, side);
		} else {
			openSingle(player, level, pos);
		}
	}

	/** Pestaña arrastrada fuera de la barra: el contenedor abierto sigue y {@code pos} se abre al lado. */
	public static void detach(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		BlockPos current = current(player);
		if (current == null || pos.equals(current) || !inGroupAndReachable(player, level, current, pos)) {
			return;
		}
		openDual(player, level, current, pos);
	}

	/** La ventana separada se solto sobre la barra: volver a una sola ventana con el contenedor principal. */
	public static void attach(ServerPlayer player) {
		BlockPos current = current(player);
		if (current != null && side(player) != null) {
			openSingle(player, player.level(), current);
		}
	}

	public static void forget(ServerPlayer player) {
		CURRENT.remove(player.getUUID());
		SIDE.remove(player.getUUID());
	}

	/** Contenedor principal que el jugador tiene abierto ahora mismo, o null. */
	public static BlockPos current(ServerPlayer player) {
		BlockPos pos = CURRENT.get(player.getUUID());
		if (pos == null || player.containerMenu == player.inventoryMenu || !isStorage(player.level(), pos)) {
			return null;
		}
		return pos;
	}

	/** Contenedor de la ventana separada, si el jugador tiene abierto el menu doble. */
	public static BlockPos side(ServerPlayer player) {
		BlockPos pos = SIDE.get(player.getUUID());
		if (pos == null || !(player.containerMenu instanceof DualContainerMenu) || !isStorage(player.level(), pos)) {
			return null;
		}
		return pos;
	}

	/** Mismas condiciones que abrirlo a mano: no espectador, a su alcance y con permiso para tocarlo. */
	public static boolean canReach(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return !player.isSpectator() && isAvailable(player, level, pos) && level.mayInteract(player, pos);
	}

	private static boolean inGroupAndReachable(ServerPlayer player, ServerLevel level, BlockPos current, BlockPos pos) {
		if (current == null || !group(level, current).contains(pos)) {
			CosmosTabs.debug("rechazado: {} no esta pegado al contenedor abierto", pos.toShortString());
			return false;
		}
		if (!player.containerMenu.getCarried().isEmpty() || !canReach(player, level, pos)) {
			CosmosTabs.debug("rechazado: lleva={} alcance/permiso={}", !player.containerMenu.getCarried().isEmpty(),
					canReach(player, level, pos));
			return false;
		}
		return true;
	}

	/** Abre {@code pos} como si el jugador le hiciera clic derecho con la mano vacia (mismos permisos). */
	private static void openSingle(ServerPlayer player, ServerLevel level, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		InteractionResult callback = UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
		if (callback != InteractionResult.PASS) {
			// Otro mod se hizo cargo (o lo prohibio): no abrimos nada por nuestra cuenta.
			CosmosTabs.debug("rechazado: UseBlockCallback devolvio {}", callback);
			return;
		}
		InteractionResult result = level.getBlockState(pos).useWithoutItem(level, player, hit);
		CosmosTabs.debug("useWithoutItem -> {}", result);
		if (result.consumesAction()) {
			CURRENT.put(player.getUUID(), pos);
			SIDE.remove(player.getUUID());
			sendTabs(player, pos);
		}
	}

	private static void openDual(ServerPlayer player, ServerLevel level, BlockPos main, BlockPos side) {
		// Antes de tocar nada (abrir los contenedores genera su botin): el cliente tiene que saber abrirlo.
		if (!ServerPlayNetworking.canSend(player, DualOpenPayload.TYPE)) {
			player.sendOverlayMessage(Component.literal("Tu versión de Cosmos Tabs no admite dos ventanas: actualizala"));
			return;
		}
		Container mainContainer = ItemClipboard.containerAt(player, level, main);
		Container sideContainer = ItemClipboard.containerAt(player, level, side);
		if (mainContainer == null || sideContainer == null || mainContainer == sideContainer) {
			return;
		}
		Component mainTitle = nameOf(level, main);
		Component sideTitle = nameOf(level, side);
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return mainTitle;
			}

			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				// Se avisa antes de que salga el paquete de apertura (que dira "cofre grande").
				ServerPlayNetworking.send(player, new DualOpenPayload(id, mainContainer.getContainerSize(),
						sideContainer.getContainerSize(), sideTitle));
				// El cofre de Ender abre la tapa (y el recuento lo reconoce) solo si sabe cual bloque es.
				markEnderChest(inventory, level, main);
				markEnderChest(inventory, level, side);
				return new DualContainerMenu(id, inventory, mainContainer, sideContainer, sideTitle);
			}
		});
		if (player.containerMenu instanceof DualContainerMenu) {
			CURRENT.put(player.getUUID(), main);
			SIDE.put(player.getUUID(), side);
			sendTabs(player, main);
		}
	}

	private static void markEnderChest(Inventory inventory, ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof EnderChestBlockEntity ender) {
			inventory.player.getEnderChestInventory().setActiveChest(ender);
		}
	}

	/**
	 * Contenedores de almacenamiento conectados a {@code start} cara con cara, en forma canonica y
	 * ordenados (arriba-abajo, luego por posicion) para que las pestañas no cambien de orden.
	 */
	static List<BlockPos> group(ServerLevel level, BlockPos start) {
		Set<BlockPos> seen = new LinkedHashSet<>();
		Set<BlockPos> found = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		seen.add(start);
		while (!queue.isEmpty() && found.size() < MAX_TABS && seen.size() <= MAX_SCAN) {
			BlockPos pos = queue.poll();
			found.add(canonical(level, pos));
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (level.isLoaded(next) && isStorage(level, next) && seen.add(next)) {
					queue.add(next);
				}
			}
		}
		List<BlockPos> sorted = new ArrayList<>(found);
		sorted.sort(Comparator.comparingInt((BlockPos pos) -> -pos.getY())
				.thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		return sorted;
	}

	private static Component nameOf(ServerLevel level, BlockPos pos) {
		return level.getBlockEntity(pos) instanceof Nameable nameable
				? nameable.getDisplayName()
				: level.getBlockState(pos).getBlock().getName();
	}

	private static void sendTabs(ServerPlayer player, BlockPos current) {
		if (!ServerPlayNetworking.canSend(player, TabsPayload.TYPE)) {
			return;
		}
		ServerLevel level = player.level();
		BlockPos side = SIDE.get(player.getUUID());
		List<TabsPayload.Tab> tabs = new ArrayList<>();
		for (BlockPos pos : group(level, current)) {
			if (!pos.equals(current) && !isAvailable(player, level, pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			tabs.add(new TabsPayload.Tab(pos, state.getBlock().asItem().getDefaultInstance(), nameOf(level, pos),
					pos.equals(current), pos.equals(side)));
		}
		// Un contenedor suelto no lleva pestañas: solo la barra para mover la ventana.
		ServerPlayNetworking.send(player, new TabsPayload(tabs.size() > 1 ? tabs : List.of()));
	}

	private static boolean isAvailable(ServerPlayer player, ServerLevel level, BlockPos pos) {
		return level.isLoaded(pos) && player.isWithinBlockInteractionRange(pos, RANGE_BUFFER) && isStorage(level, pos);
	}

	/** Las dos mitades de un cofre doble son el mismo contenedor: se guarda siempre la misma. */
	static BlockPos canonical(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
			return other.asLong() < pos.asLong() ? other : pos;
		}
		return pos.immutable();
	}
}
