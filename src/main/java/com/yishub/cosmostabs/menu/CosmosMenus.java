package com.yishub.cosmostabs.menu;

import com.yishub.cosmostabs.CosmosTabs;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;

public final class CosmosMenus {
	public static final MenuType<DualContainerMenu> DUAL = Registry.register(BuiltInRegistries.MENU, CosmosTabs.id("dual"),
			new ExtendedMenuType<>(DualContainerMenu::new, DualContainerMenu.Data.STREAM_CODEC));

	private CosmosMenus() {}

	/** Fuerza la carga de la clase (y el registro) desde el inicializador del mod. */
	public static void init() {}
}
