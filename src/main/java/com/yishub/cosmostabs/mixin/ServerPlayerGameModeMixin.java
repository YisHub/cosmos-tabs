package com.yishub.cosmostabs.mixin;

import com.yishub.cosmostabs.server.RecentContainers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Detecta cuando un clic derecho sobre un bloque abre un menu nuevo (cofre, barril, horno...). */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@Unique
	private AbstractContainerMenu cosmostabs$menuBefore;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void cosmostabs$rememberMenu(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		this.cosmostabs$menuBefore = player.containerMenu;
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void cosmostabs$recordOpened(ServerPlayer player, Level level, ItemStack itemStack, InteractionHand hand,
			BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
		AbstractContainerMenu menu = player.containerMenu;
		if (menu != this.cosmostabs$menuBefore && menu != player.inventoryMenu) {
			RecentContainers.opened(player, hitResult.getBlockPos());
		}
		this.cosmostabs$menuBefore = null;
	}
}
