package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Cliente -> servidor: Ctrl+C sobre las ranuras marcadas (indices dentro del contenedor).
 * {@code side}: las ranuras son de la ventana separada, no de la principal.
 */
public record CopyItemsPayload(List<Integer> slots, boolean side) implements CustomPacketPayload {
	public static final Type<CopyItemsPayload> TYPE = new Type<>(CosmosTabs.id("copy_items"));
	public static final StreamCodec<RegistryFriendlyByteBuf, CopyItemsPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(256)), CopyItemsPayload::slots,
			ByteBufCodecs.BOOL, CopyItemsPayload::side,
			CopyItemsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
