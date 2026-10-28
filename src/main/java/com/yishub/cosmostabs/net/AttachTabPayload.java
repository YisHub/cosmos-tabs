package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: la ventana separada se solto sobre la barra (o se cerro): volver a una sola ventana. */
public record AttachTabPayload() implements CustomPacketPayload {
	public static final Type<AttachTabPayload> TYPE = new Type<>(CosmosTabs.id("attach_tab"));
	public static final StreamCodec<RegistryFriendlyByteBuf, AttachTabPayload> STREAM_CODEC = StreamCodec.unit(new AttachTabPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
