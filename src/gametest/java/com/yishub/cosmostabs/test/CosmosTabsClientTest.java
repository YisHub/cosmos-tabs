package com.yishub.cosmostabs.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.yishub.cosmostabs.client.CosmosTabsClient;
import com.yishub.cosmostabs.client.MovableWindow;
import com.yishub.cosmostabs.net.TabsPayload;
import com.yishub.cosmostabs.test.mixin.ContainerScreenAccessor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Prueba de punta a punta: abre contenedores reales con clic derecho, comprueba las pestañas,
 * cambia de pestaña, arrastra la ventana y saca una captura en cada paso (carpeta screenshots/).
 * Cada comprobacion escribe "[cosmostabs-test] PASS/FAIL" en el log.
 */
public class CosmosTabsClientTest implements FabricClientGameTest {
	private static final Logger LOG = LoggerFactory.getLogger("cosmostabs-test");
	private static final int TAB_W = 22;

	private final List<String> failures = new ArrayList<>();
	private int shot;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		TestInput input = ctx.getInput();
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getConnection().waitForChunksRender();

			// --- Escenario: 3 cofres con nombre, un barril y un horno alrededor del jugador ---
			BlockPos p = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			BlockPos chestA = p.offset(2, 0, -1);
			BlockPos chestB = p.offset(2, 0, 1);
			BlockPos barrel = p.offset(-2, 0, 0);
			BlockPos furnace = p.offset(0, 0, 2);
			BlockPos chestC = p.offset(0, 0, -2);
			cmd(sp, "fill %s %s minecraft:air", p.offset(-4, 0, -4), p.offset(4, 3, 4));
			cmd(sp, "fill %s %s minecraft:stone", p.offset(-4, -1, -4), p.offset(4, -1, 4));
			cmd(sp, "tp @p %d %d %d 0 0", p.getX(), p.getY(), p.getZ());
			cmd(sp, "time set noon");
			cmd(sp, "setblock %s minecraft:chest{CustomName:\"Cofre A\"}", chestA);
			cmd(sp, "setblock %s minecraft:chest{CustomName:\"Cofre B\"}", chestB);
			cmd(sp, "setblock %s minecraft:chest{CustomName:\"Cofre C\"}", chestC);
			cmd(sp, "setblock %s minecraft:barrel", barrel);
			cmd(sp, "setblock %s minecraft:furnace", furnace);
			cmd(sp, "item replace block %s container.0 with minecraft:diamond 5", chestA);
			cmd(sp, "item replace block %s container.0 with minecraft:emerald 7", chestB);
			cmd(sp, "item replace block %s container.0 with minecraft:gold_ingot 3", chestC);
			cmd(sp, "item replace block %s container.0 with minecraft:apple 9", barrel);
			sp.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			screenshot(ctx, "escenario");

			// --- Abrir contenedores con clic derecho real (pasa por el servidor como un jugador) ---
			open(ctx, input, chestA, "Cofre A");
			screenshot(ctx, "cofre-a-primera-vez");
			close(ctx, input);
			open(ctx, input, chestB, "Cofre B");
			close(ctx, input);
			open(ctx, input, barrel, null);
			close(ctx, input);

			// Horno: con Cinematic Interactions abre su escena 3D en vez de la ventana.
			input.lookAt(furnace);
			input.pressKey(o -> o.keyUse);
			ctx.waitTicks(60);
			screenshot(ctx, "horno-cinematic");
			for (int i = 0; i < 5 && ctx.computeOnClient(mc -> mc.gui.screen() != null); i++) {
				input.pressKey(InputConstants.KEY_ESCAPE);
				ctx.waitTicks(20);
			}
			ctx.waitTicks(20);

			open(ctx, input, chestC, "Cofre C");

			// --- Pestañas ---
			waitSoft(ctx, mc -> tabs(mc).size() >= 4, 100);
			List<TabsPayload.Tab> tabs = ctx.computeOnClient(CosmosTabsClientTest::tabs);
			screenshot(ctx, "pestanas");
			check(tabs.size() == 4, "hay 4 pestañas (A, B, barril, C)", "pestañas=" + names(tabs));
			check(tabs.stream().noneMatch(t -> t.icon().is(Items.FURNACE)), "el horno no tiene pestaña", names(tabs));
			check(tabs.stream().anyMatch(t -> t.current() && t.name().getString().equals("Cofre C")),
					"la pestaña actual es Cofre C", names(tabs));

			// --- Cambiar de pestaña con clic: Cofre A ---
			int tabA = indexOf(tabs, "Cofre A");
			check(tabA >= 0, "existe la pestaña de Cofre A", names(tabs));
			if (tabA >= 0) {
				int[] win = window(ctx);
				clickGui(ctx, input, win[0] + tabA * (TAB_W + 1) + TAB_W / 2, win[1] - 10, InputConstants.MOUSE_BUTTON_LEFT);
				boolean switched = waitSoft(ctx, mc -> title(mc).equals("Cofre A"), 100);
				ctx.waitTicks(10);
				screenshot(ctx, "cambio-a-cofre-a");
				check(switched, "clic en la pestaña abre Cofre A", "titulo=" + ctx.computeOnClient(CosmosTabsClientTest::title));
				check(ctx.computeOnClient(mc -> tabs(mc).stream().anyMatch(t -> t.current() && t.name().getString().equals("Cofre A"))),
						"la pestaña actual pasa a ser Cofre A", names(ctx.computeOnClient(CosmosTabsClientTest::tabs)));
			}

			// --- Arrastrar la ventana por la barra ---
			int[] before = window(ctx);
			int n = ctx.computeOnClient(mc -> tabs(mc).size());
			int gripX = (before[0] + n * (TAB_W + 1) + before[0] + before[2]) / 2;
			int gripY = before[1] - 5;
			moveGui(ctx, input, gripX, gripY);
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTick();
			for (int i = 1; i <= 10; i++) {
				moveGui(ctx, input, gripX - 6 * i, gripY + 3 * i);
				ctx.waitTick();
			}
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);
			int[] after = window(ctx);
			Path dragged = screenshot(ctx, "ventana-arrastrada");
			check(after[0] == before[0] - 60 && after[1] == before[1] + 30, "arrastrar mueve la ventana (-60, +30)",
					"antes=" + before[0] + "," + before[1] + " despues=" + after[0] + "," + after[1]);

			// El fondo tiene que moverse con el contenido: gris de la interfaz dentro de la esquina, mundo fuera.
			int scale = ctx.computeOnClient(mc -> mc.getWindow().getGuiScale());
			int inside = pixel(dragged, (after[0] + 4) * scale, (after[1] + 4) * scale);
			int outside = pixel(dragged, (after[0] - 4) * scale, (after[1] + 40) * scale);
			check(isGuiGrey(inside) && !isGuiGrey(outside), "el fondo de la ventana se mueve con ella",
					String.format("dentro=#%06X fuera=#%06X", inside, outside));

			// Con la ventana movida, un clic en la primera ranura tiene que recoger los diamantes de Cofre A.
			clickGui(ctx, input, after[0] + 8 + 8, after[1] + 18 + 8, InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);
			boolean picked = ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> s
					&& s.getMenu().getCarried().is(Items.DIAMOND));
			screenshot(ctx, "clic-en-ranura-movida");
			check(picked, "con la ventana movida, clic en la ranura recoge los diamantes",
					"lleva=" + ctx.computeOnClient(mc -> mc.gui.screen() instanceof AbstractContainerScreen<?> s
							? s.getMenu().getCarried().toString() : "-"));
			clickGui(ctx, input, after[0] + 8 + 8, after[1] + 18 + 8, InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);

			// --- La posicion se recuerda al volver a abrir ---
			close(ctx, input);
			open(ctx, input, chestB, "Cofre B");
			int[] reopened = window(ctx);
			screenshot(ctx, "recuerda-posicion");
			check(reopened[0] == after[0] && reopened[1] == after[1], "al reabrir conserva la posicion",
					"esperada=" + after[0] + "," + after[1] + " real=" + reopened[0] + "," + reopened[1]);

			// --- Clic derecho en la barra: vuelve al centro ---
			int n2 = ctx.computeOnClient(mc -> tabs(mc).size());
			clickGui(ctx, input, (reopened[0] + n2 * (TAB_W + 1) + reopened[0] + reopened[2]) / 2, reopened[1] - 5,
					InputConstants.MOUSE_BUTTON_RIGHT);
			ctx.waitTicks(5);
			int[] centered = window(ctx);
			int[] expected = ctx.computeOnClient(mc -> {
				AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.gui.screen();
				int w = ((ContainerScreenAccessor) s).cosmostabsTest$imageWidth();
				return new int[] {(s.width - w) / 2};
			});
			screenshot(ctx, "centrada");
			check(centered[0] == expected[0], "clic derecho en la barra la centra",
					"x=" + centered[0] + " esperada=" + expected[0]);
			close(ctx, input);
		}

		if (failures.isEmpty()) {
			LOG.info("[cosmostabs-test] RESULTADO: TODO OK");
		} else {
			LOG.error("[cosmostabs-test] RESULTADO: {} fallo(s): {}", failures.size(), failures);
			throw new AssertionError("Cosmos Tabs: " + failures);
		}
	}

	// ---------- ayudas ----------

	private static void cmd(TestSingleplayerContext sp, String format, Object... args) {
		Object[] formatted = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			formatted[i] = args[i] instanceof BlockPos b ? b.getX() + " " + b.getY() + " " + b.getZ() : args[i];
		}
		sp.getServer().runCommand(String.format(format, formatted));
	}

	private void open(ClientGameTestContext ctx, TestInput input, BlockPos pos, String expectedTitle) {
		input.lookAt(pos);
		input.pressKey(o -> o.keyUse);
		boolean ok = waitSoft(ctx, mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>
				&& (expectedTitle == null || title(mc).equals(expectedTitle)), 100);
		check(ok, "se abre " + (expectedTitle != null ? expectedTitle : "el contenedor en " + pos.toShortString()),
				"pantalla=" + ctx.computeOnClient(mc -> mc.gui.screen() == null ? "ninguna" : mc.gui.screen().getClass().getSimpleName()));
		ctx.waitTicks(10);
	}

	private static void close(ClientGameTestContext ctx, TestInput input) {
		input.pressKey(InputConstants.KEY_ESCAPE);
		waitSoft(ctx, mc -> mc.gui.screen() == null, 60);
		ctx.waitTicks(5);
	}

	private static boolean waitSoft(ClientGameTestContext ctx, Predicate<Minecraft> condition, int ticks) {
		try {
			ctx.waitFor(condition, ticks);
			return true;
		} catch (AssertionError e) {
			return false;
		}
	}

	private void check(boolean ok, String what, String detail) {
		if (ok) {
			LOG.info("[cosmostabs-test] PASS {}", what);
		} else {
			LOG.error("[cosmostabs-test] FAIL {} ({})", what, detail);
			failures.add(what);
		}
	}

	private Path screenshot(ClientGameTestContext ctx, String name) {
		Path path = ctx.takeScreenshot(String.format("cosmostabs-%02d-%s", ++shot, name));
		LOG.info("[cosmostabs-test] captura {}", path);
		return path;
	}

	/** Color RGB de un pixel de la captura (o -1 si no se pudo leer). */
	private static int pixel(Path png, int x, int y) {
		try {
			BufferedImage image = ImageIO.read(png.toFile());
			return image.getRGB(x, y) & 0xFFFFFF;
		} catch (IOException | RuntimeException e) {
			LOG.warn("[cosmostabs-test] no se pudo leer {} en ({}, {})", png, x, y, e);
			return -1;
		}
	}

	/** El gris de fondo de las ventanas vanilla es #C6C6C6. */
	private static boolean isGuiGrey(int rgb) {
		if (rgb < 0) {
			return false;
		}
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		return Math.abs(r - 0xC6) <= 12 && Math.abs(g - 0xC6) <= 12 && Math.abs(b - 0xC6) <= 12;
	}

	private static List<TabsPayload.Tab> tabs(Minecraft mc) {
		return mc.gui.screen() instanceof AbstractContainerScreen<?> s ? CosmosTabsClient.tabsFor(s.getMenu().containerId) : List.of();
	}

	private static String title(Minecraft mc) {
		return mc.gui.screen() == null ? "" : mc.gui.screen().getTitle().getString();
	}

	private static String names(List<TabsPayload.Tab> tabs) {
		return tabs.stream().map(t -> t.name().getString() + (t.current() ? "*" : "")).toList().toString();
	}

	private static int indexOf(List<TabsPayload.Tab> tabs, String name) {
		for (int i = 0; i < tabs.size(); i++) {
			if (tabs.get(i).name().getString().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	/** {x, y, ancho} de la ventana abierta tal como se ve en pantalla (posicion interna + desplazamiento). */
	private static int[] window(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			if (!(mc.gui.screen() instanceof ContainerScreenAccessor s) || !(mc.gui.screen() instanceof MovableWindow w)) {
				return new int[] {-1, -1, 0};
			}
			return new int[] {s.cosmostabsTest$leftPos() + w.cosmostabs$offsetX(),
					s.cosmostabsTest$topPos() + w.cosmostabs$offsetY(), s.cosmostabsTest$imageWidth()};
		});
	}

	/** El cursor de prueba va en pixeles de ventana; la GUI usa coordenadas escaladas. */
	private static void moveGui(ClientGameTestContext ctx, TestInput input, double guiX, double guiY) {
		double[] raw = ctx.computeOnClient(mc -> {
			Window w = mc.getWindow();
			return new double[] {guiX * w.getScreenWidth() / w.getGuiScaledWidth() + 0.5,
					guiY * w.getScreenHeight() / w.getGuiScaledHeight() + 0.5};
		});
		input.setCursorPos(raw[0], raw[1]);
	}

	private static void clickGui(ClientGameTestContext ctx, TestInput input, double guiX, double guiY, int button) {
		moveGui(ctx, input, guiX, guiY);
		ctx.waitTick();
		input.pressMouse(button);
	}
}
