package com.yishub.cosmostabs.client.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Las ranuras de la ventana separada se recolocan cuando se arrastra (x/y son final en vanilla). */
@Mixin(Slot.class)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void cosmostabs$setX(int x);

	@Mutable
	@Accessor("y")
	void cosmostabs$setY(int y);
}
