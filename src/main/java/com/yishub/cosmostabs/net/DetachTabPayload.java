package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: se arrastro la pestaña de {@code pos} fuera de la barra: abrirla en otra ventana. */
public record DetachTabPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<DetachTabPayload> TYPE = new Type<>(CosmosTabs.id("detach_tab"));
	public static final StreamCodec<RegistryFriendlyByteBuf, DetachTabPayload> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, DetachTabPayload::pos,
			DetachTabPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
