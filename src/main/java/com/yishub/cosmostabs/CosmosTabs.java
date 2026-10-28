package com.yishub.cosmostabs;

import com.yishub.cosmostabs.menu.CosmosMenus;
import com.yishub.cosmostabs.net.AttachTabPayload;
import com.yishub.cosmostabs.net.CutItemsPayload;
import com.yishub.cosmostabs.net.DetachTabPayload;
import com.yishub.cosmostabs.net.PasteItemsPayload;
import com.yishub.cosmostabs.net.SwitchTabPayload;
import com.yishub.cosmostabs.net.TabsPayload;
import com.yishub.cosmostabs.server.ContainerGroups;
import com.yishub.cosmostabs.server.ItemClipboard;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;

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

		PayloadTypeRegistry.serverboundPlay().register(CutItemsPayload.TYPE, CutItemsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PasteItemsPayload.TYPE, PasteItemsPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(DetachTabPayload.TYPE, DetachTabPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AttachTabPayload.TYPE, AttachTabPayload.STREAM_CODEC);
		CosmosMenus.init();

		ServerPlayNetworking.registerGlobalReceiver(SwitchTabPayload.TYPE,
				(payload, context) -> ContainerGroups.switchTo(context.player(), payload.pos()));
		ServerPlayNetworking.registerGlobalReceiver(CutItemsPayload.TYPE,
				(payload, context) -> ItemClipboard.cut(context.player(), payload.slots(), payload.side()));
		ServerPlayNetworking.registerGlobalReceiver(PasteItemsPayload.TYPE,
				(payload, context) -> ItemClipboard.paste(context.player(), payload.side()));
		ServerPlayNetworking.registerGlobalReceiver(DetachTabPayload.TYPE,
				(payload, context) -> ContainerGroups.detach(context.player(), payload.pos()));
		ServerPlayNetworking.registerGlobalReceiver(AttachTabPayload.TYPE,
				(payload, context) -> ContainerGroups.attach(context.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ContainerGroups.forget(handler.getPlayer());
			ItemClipboard.forget(handler.getPlayer());
		});

		if (AUDIT) {
			ServerLifecycleEvents.SERVER_STARTED.register(server -> {
				audit();
				server.halt(false);
			});
		}
	}

	/** Prueba de compatibilidad (-Dcosmostabs.audit=true): aplica ya todos los mixins de todos los mods. */
	public static final boolean AUDIT = Boolean.getBoolean("cosmostabs.audit");

	/** -Dcosmostabs.debug=true: explica en el log cada clic en pestaña y por que se acepta o rechaza. */
	public static final boolean DEBUG = Boolean.getBoolean("cosmostabs.debug");

	public static void debug(String message, Object... args) {
		if (DEBUG) {
			LOGGER.info("[cosmostabs-debug] " + message, args);
		}
	}

	public static void audit() {
		LOGGER.info("[cosmostabs-audit] aplicando todos los mixins...");
		try {
			MixinEnvironment.getCurrentEnvironment().audit();
			LOGGER.info("[cosmostabs-audit] OK: ningun mixin fallo");
		} catch (Throwable t) {
			LOGGER.error("[cosmostabs-audit] FALLO", t);
		}
	}
}
