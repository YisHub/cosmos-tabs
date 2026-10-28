package com.yishub.cosmostabs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Lo implementan (via mixin) las pantallas de contenedores. La ventana nunca cambia su posicion
 * interna: se dibuja trasladada {@code (offsetX, offsetY)} y al raton se le resta lo mismo, asi
 * cualquier pantalla (aunque calcule su fondo por su cuenta) se mueve entera.
 */
public interface MovableWindow {
	int cosmostabs$offsetX();

	int cosmostabs$offsetY();

	/** Desplazamiento de la pantalla abierta ahora mismo, o {0, 0} si no se puede mover. */
	static int[] currentOffset() {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen instanceof MovableWindow window) {
			return new int[] {window.cosmostabs$offsetX(), window.cosmostabs$offsetY()};
		}
		return new int[] {0, 0};
	}
}
