package com.yishub.cosmostabs.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.yishub.cosmostabs.client.CosmosTabsClient;
import com.yishub.cosmostabs.client.DualContainerScreen;
import com.yishub.cosmostabs.menu.DualContainerMenu;
import com.yishub.cosmostabs.net.DualOpenPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	/**
	 * Al cambiar de pestaña el servidor cierra el contenedor y enseguida abre el otro. Cerrar la
	 * pantalla captura el raton (lo manda a la mira) y abrir la siguiente lo suelta en el centro. Si el
	 * cierre que manda el servidor lo provoco una pestaña, se ignora: la pantalla nueva reemplaza a la
	 * vieja directamente y el raton no se mueve. Los cierres del propio jugador (Esc) no pasan por aqui.
	 */
	@WrapWithCondition(method = "handleContainerClose",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"))
	private boolean cosmostabs$keepScreenWhileSwitching(LocalPlayer player) {
		return !CosmosTabsClient.consumeSwitchPending();
	}

	/**
	 * El menu doble viaja como "cofre grande" vanilla (asi no hay que registrar un tipo propio y
	 * pueden entrar jugadores sin el mod). Si el servidor aviso que este menu es doble, se abre la
	 * pantalla de dos ventanas en su lugar.
	 */
	@Inject(method = "handleOpenScreen", cancellable = true,
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
	private void cosmostabs$openDual(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
		DualOpenPayload dual = CosmosTabsClient.takeDual(packet.getContainerId());
		// Solo un "cofre grande" puede ser doble, y con tamaños de contenedor razonables.
		if (dual == null || packet.getType() != MenuType.GENERIC_9x6
				|| !DualContainerMenu.validSize(dual.mainSize()) || !DualContainerMenu.validSize(dual.sideSize())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		DualContainerMenu menu = new DualContainerMenu(packet.getContainerId(), mc.player.getInventory(),
				dual.mainSize(), dual.sideSize(), dual.sideTitle());
		mc.player.containerMenu = menu;
		mc.gui.setScreen(new DualContainerScreen(menu, mc.player.getInventory(), packet.getTitle()));
		ci.cancel();
	}
}
