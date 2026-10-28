package com.yishub.cosmostabs;

import com.yishub.cosmostabs.net.SwitchTabPayload;
import com.yishub.cosmostabs.net.TabsPayload;
import com.yishub.cosmostabs.server.RecentContainers;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CosmosTabs implements ModInitializer {
	public static final String MOD_ID = "cosmostabs";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(TabsPayload.TYPE, TabsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SwitchTabPayload.TYPE, SwitchTabPayload.STREAM_CODEC);

		ServerPlayNetworking.registerGlobalReceiver(SwitchTabPayload.TYPE,
				(payload, context) -> RecentContainers.switchTo(context.player(), payload.pos()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RecentContainers.forget(handler.getPlayer()));
	}
}
