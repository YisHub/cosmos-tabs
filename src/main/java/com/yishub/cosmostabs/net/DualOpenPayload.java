package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Servidor -> cliente, justo antes de abrir el menu {@code containerId}: ese menu es doble
 * (DualContainerMenu) aunque el paquete de apertura diga "cofre grande". Asi no hace falta
 * registrar un tipo de menu propio, que obligaria a todos los jugadores a tener el mod.
 */
public record DualOpenPayload(int containerId, int mainSize, int sideSize, Component sideTitle) implements CustomPacketPayload {
	public static final Type<DualOpenPayload> TYPE = new Type<>(CosmosTabs.id("dual_open"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DualOpenPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DualOpenPayload::containerId,
			ByteBufCodecs.VAR_INT, DualOpenPayload::mainSize,
			ByteBufCodecs.VAR_INT, DualOpenPayload::sideSize,
			ComponentSerialization.STREAM_CODEC, DualOpenPayload::sideTitle,
			DualOpenPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
