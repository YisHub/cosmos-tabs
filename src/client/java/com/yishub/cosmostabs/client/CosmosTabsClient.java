package com.yishub.cosmostabs.client;

import com.yishub.cosmostabs.CosmosTabs;
import com.yishub.cosmostabs.net.TabsPayload;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class CosmosTabsClient implements ClientModInitializer {
	/** Pestañas que mando el servidor y el containerId del menu al que corresponden. */
	private static List<TabsPayload.Tab> tabs = List.of();
	private static int tabsContainerId = -1;

	@Override
	public void onInitializeClient() {
		WindowPositions.load();
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

	/** Las pestañas solo valen para el menu que estaba abierto cuando llegaron. */
	public static List<TabsPayload.Tab> tabsFor(int containerId) {
		return containerId == tabsContainerId ? tabs : List.of();
	}
}
