package com.yishub.cosmostabs.test.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int cosmostabsTest$leftPos();

	@Accessor("topPos")
	int cosmostabsTest$topPos();

	@Accessor("imageWidth")
	int cosmostabsTest$imageWidth();
}
