package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Cliente -> servidor: el jugador hizo clic en la pestaña del contenedor en {@code pos}. */
public record SwitchTabPayload(BlockPos pos) implements CustomPacketPayload {
	public static final Type<SwitchTabPayload> TYPE = new Type<>(CosmosTabs.id("switch_tab"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchTabPayload> STREAM_CODEC = StreamCodec.composite(
			net.minecraft.core.BlockPos.STREAM_CODEC, SwitchTabPayload::pos,
			SwitchTabPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
