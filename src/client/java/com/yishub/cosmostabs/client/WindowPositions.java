package com.yishub.cosmostabs.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.yishub.cosmostabs.CosmosTabs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Desplazamiento de cada tipo de ventana respecto al centro de la pantalla.
 * Se guarda en config/cosmostabs.json para que la ventana aparezca donde la dejaste.
 */
public final class WindowPositions {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("cosmostabs.json");
	private static Map<String, int[]> offsets = new HashMap<>();

	private WindowPositions() {}

	public static int[] get(String key) {
		return offsets.getOrDefault(key, new int[] {0, 0});
	}

	public static void set(String key, int dx, int dy) {
		if (dx == 0 && dy == 0) {
			offsets.remove(key);
		} else {
			offsets.put(key, new int[] {dx, dy});
		}
		save();
	}

	static void load() {
		if (!Files.exists(FILE)) {
			return;
		}
		try {
			Map<String, int[]> loaded = GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, int[]>>() {}.getType());
			if (loaded != null) {
				offsets = new HashMap<>(loaded);
			}
		} catch (IOException | RuntimeException e) {
			CosmosTabs.LOGGER.warn("No se pudo leer {}", FILE, e);
		}
	}

	private static void save() {
		try {
			Files.writeString(FILE, GSON.toJson(offsets));
		} catch (IOException e) {
			CosmosTabs.LOGGER.warn("No se pudo guardar {}", FILE, e);
		}
	}
}
