package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: Ctrl+V, mover lo copiado al contenedor abierto ({@code side}: al de la ventana separada). */
public record PasteItemsPayload(boolean side) implements CustomPacketPayload {
	public static final Type<PasteItemsPayload> TYPE = new Type<>(CosmosTabs.id("paste_items"));
	public static final StreamCodec<RegistryFriendlyByteBuf, PasteItemsPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, PasteItemsPayload::side,
			PasteItemsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
