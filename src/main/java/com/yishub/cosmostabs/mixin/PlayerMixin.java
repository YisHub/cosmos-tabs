package com.yishub.cosmostabs.mixin;

import com.yishub.cosmostabs.menu.DualContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cada 5 ticks el cofre recuenta quien lo tiene abierto y solo reconoce los menus vanilla. Sin
 * esto, con dos ventanas el recuento da 0, la tapa se cierra y al cerrar el menu doble el
 * contador queda en -1: desde ahi la animacion de la tapa queda desfasada (cofres abiertos).
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "hasContainerOpen(Lnet/minecraft/world/level/block/entity/ContainerOpenersCounter;Lnet/minecraft/core/BlockPos;)Z",
			at = @At("RETURN"), cancellable = true)
	private void cosmostabs$countDualMenu(ContainerOpenersCounter counter, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (!cir.getReturnValueZ() && self.containerMenu instanceof DualContainerMenu dual
				&& dual.holds(self.level().getBlockEntity(pos))) {
			cir.setReturnValue(true);
		}
	}
}
