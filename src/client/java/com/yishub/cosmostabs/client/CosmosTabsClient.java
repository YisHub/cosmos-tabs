package com.yishub.cosmostabs.client;

import com.yishub.cosmostabs.CosmosTabs;
import com.yishub.cosmostabs.menu.CosmosMenus;
import com.yishub.cosmostabs.menu.DualContainerMenu;
import net.minecraft.client.gui.screens.MenuScreens;
import com.yishub.cosmostabs.net.TabsPayload;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CosmosTabsClient implements ClientModInitializer {
	/** Pestañas que mando el servidor y el containerId del menu al que corresponden. */
	private static List<TabsPayload.Tab> tabs = List.of();
	private static int tabsContainerId = -1;

	private static final String SIDE_KEY = "cosmostabs:ventana-separada";

	/** Donde va la ventana separada, relativa a la principal. */
	public static int[] sidePanelPosition() {
		int[] saved = WindowPositions.get(SIDE_KEY);
		return saved[0] == 0 && saved[1] == 0
				? new int[] {DualContainerMenu.SIDE_DEFAULT_X, DualContainerMenu.SIDE_DEFAULT_Y}
				: saved;
	}

	public static void setSidePanelPosition(int x, int y) {
		WindowPositions.set(SIDE_KEY, x, y);
	}

	@Override
	public void onInitializeClient() {
		WindowPositions.load();
		MenuScreens.register(CosmosMenus.DUAL, DualContainerScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(TabsPayload.TYPE, (payload, context) -> {
			tabs = payload.tabs();
			tabsContainerId = context.player().containerMenu.containerId;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			tabs = List.of();
			tabsContainerId = -1;
		});

		if (CosmosTabs.AUDIT) {
			// Los mods se inicializan antes de que el juego cree la ventana: la auditoria no
			// necesita pantalla, asi que se hace aqui y se sale sin llegar a abrirla.
			CosmosTabs.audit();
			Runtime.getRuntime().halt(0);
		}
	}

	/** Momento (ms) hasta el que se espera el cierre provocado por un clic en una pestaña. */
	private static long switchPendingUntil;

	public static void markSwitchPending() {
		switchPendingUntil = System.currentTimeMillis() + 2000;
	}

	/** True una sola vez si hay un cambio de pestaña en curso (el servidor cierra y enseguida abre otro). */
	public static boolean consumeSwitchPending() {
		boolean pending = System.currentTimeMillis() < switchPendingUntil;
		switchPendingUntil = 0;
		return pending;
	}

	/** Las pestañas solo valen para el menu que estaba abierto cuando llegaron. */
	public static List<TabsPayload.Tab> tabsFor(int containerId) {
		return containerId == tabsContainerId ? tabs : List.of();
	}
}
