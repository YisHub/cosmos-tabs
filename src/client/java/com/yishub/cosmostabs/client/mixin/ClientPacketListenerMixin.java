package com.yishub.cosmostabs.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.yishub.cosmostabs.client.CosmosTabsClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Al cambiar de pestaña el servidor cierra el contenedor y enseguida abre el otro. Cerrar la
 * pantalla captura el raton (lo manda a la mira) y abrir la siguiente lo suelta en el centro. Si el
 * cierre que manda el servidor lo provoco una pestaña, se ignora: la pantalla nueva reemplaza a la
 * vieja directamente y el raton no se mueve. Los cierres del propio jugador (Esc) no pasan por aqui.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@WrapWithCondition(method = "handleContainerClose",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"))
	private boolean cosmostabs$keepScreenWhileSwitching(LocalPlayer player) {
		return !CosmosTabsClient.consumeSwitchPending();
	}
}
