package com.yishub.cosmostabs.net;

import com.yishub.cosmostabs.CosmosTabs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

/** Servidor -> cliente: las pestañas disponibles para la ventana abierta. */
public record TabsPayload(List<Tab> tabs) implements CustomPacketPayload {
	public static final Type<TabsPayload> TYPE = new Type<>(CosmosTabs.id("tabs"));

	public record Tab(BlockPos pos, ItemStack icon, Component name, boolean current) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Tab> STREAM_CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Tab::pos,
				ItemStack.STREAM_CODEC, Tab::icon,
				ComponentSerialization.STREAM_CODEC, Tab::name,
				ByteBufCodecs.BOOL, Tab::current,
				Tab::new);
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, TabsPayload> STREAM_CODEC = StreamCodec.composite(
			Tab.STREAM_CODEC.apply(ByteBufCodecs.list()), TabsPayload::tabs,
			TabsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
