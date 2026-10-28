package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente -> servidor: Ctrl+X (cortar) sobre las ranuras marcadas (indices dentro del contenedor).
 * {@code side}: las ranuras son de la ventana separada, no de la principal.
 */
public record CutItemsPayload(List<Integer> slots, boolean side) implements CustomPacketPayload {
	public static final Type<CutItemsPayload> TYPE = new Type<>(CosmosTabs.id("cut_items"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CutItemsPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), CutItemsPayload::slots,
			ByteBufCodecs.BOOL, CutItemsPayload::side,
			CutItemsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
