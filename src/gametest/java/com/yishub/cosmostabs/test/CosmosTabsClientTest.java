package com.yishub.cosmostabs.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.yishub.cosmostabs.client.CosmosTabsClient;
import com.yishub.cosmostabs.client.DualContainerScreen;
import net.minecraft.world.inventory.Slot;
import com.yishub.cosmostabs.client.MovableWindow;
import com.yishub.cosmostabs.net.SwitchTabPayload;
import com.yishub.cosmostabs.net.TabsPayload;
import com.yishub.cosmostabs.test.mixin.ContainerScreenAccessor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
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

			// --- Escenario: un grupo pegado en fila (Cofre A, Cofre B, barril y, pegado al barril, un
			// horno) y un Cofre C suelto al otro lado del jugador ---
			BlockPos p = sp.getServer().computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			BlockPos chestA = p.offset(2, 0, -1);
			BlockPos chestB = p.offset(2, 0, 0);
			BlockPos barrel = p.offset(2, 0, 1);
			BlockPos furnace = p.offset(2, 0, 2);
			BlockPos chestC = p.offset(-2, 0, 0);
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

			// --- Cofre suelto: sin pestañas, solo la barra para mover la ventana ---
			open(ctx, input, chestC, "Cofre C");
			ctx.waitTicks(20);
			screenshot(ctx, "cofre-suelto-sin-pestanas");
			List<TabsPayload.Tab> loneTabs = ctx.computeOnClient(CosmosTabsClientTest::tabs);
			check(loneTabs.isEmpty(), "un cofre suelto no tiene pestañas", names(loneTabs));
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

			// --- Grupo pegado: pestañas solo para A, B y el barril (ni el horno ni el Cofre C suelto) ---
			open(ctx, input, chestB, "Cofre B");
			waitSoft(ctx, mc -> tabs(mc).size() >= 3, 100);
			List<TabsPayload.Tab> tabs = ctx.computeOnClient(CosmosTabsClientTest::tabs);
			screenshot(ctx, "pestanas-del-grupo");
			check(tabs.size() == 3, "el grupo pegado tiene 3 pestañas (A, B, barril)", "pestañas=" + names(tabs));
			check(tabs.stream().noneMatch(t -> t.icon().is(Items.FURNACE)), "el horno pegado no tiene pestaña", names(tabs));
			check(indexOf(tabs, "Cofre C") < 0, "el Cofre C suelto no aparece en el grupo", names(tabs));
			check(tabs.stream().anyMatch(t -> t.current() && t.name().getString().equals("Cofre B")),
					"la pestaña actual es Cofre B", names(tabs));

			// --- Cambiar de pestaña con clic: Cofre A ---
			int tabA = indexOf(tabs, "Cofre A");
			check(tabA >= 0, "existe la pestaña de Cofre A", names(tabs));
			if (tabA >= 0) {
				int[] win = window(ctx);
				moveGui(ctx, input, win[0] + tabA * (TAB_W + 1) + TAB_W / 2, win[1] - 10);
				ctx.waitTick();
				double[] cursorBefore = ctx.computeOnClient(mc -> new double[] {mc.mouseHandler.xpos(), mc.mouseHandler.ypos()});
				input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
				boolean switched = waitSoft(ctx, mc -> title(mc).equals("Cofre A"), 100);
				ctx.waitTicks(10);
				screenshot(ctx, "cambio-a-cofre-a");
				check(switched, "clic en la pestaña abre Cofre A", "titulo=" + ctx.computeOnClient(CosmosTabsClientTest::title));
				double[] cursorAfter = ctx.computeOnClient(mc -> new double[] {mc.mouseHandler.xpos(), mc.mouseHandler.ypos()});
				boolean grabbed = ctx.computeOnClient(mc -> mc.mouseHandler.isMouseGrabbed());
				// La posicion exacta no se compara: al cambiar de pantalla Minecraft relee el cursor real del
				// sistema (resyncMousePosition), que en la prueba no es el simulado. El bug era que se capturaba.
				check(!grabbed, "al cambiar de pestaña el raton no se captura (no vuelve a la mira)",
						String.format("antes=(%.0f, %.0f) despues=(%.0f, %.0f) capturado=%s",
								cursorBefore[0], cursorBefore[1], cursorAfter[0], cursorAfter[1], grabbed));
				check(ctx.computeOnClient(mc -> tabs(mc).stream().anyMatch(t -> t.current() && t.name().getString().equals("Cofre A"))),
						"la pestaña actual pasa a ser Cofre A", names(ctx.computeOnClient(CosmosTabsClientTest::tabs)));
			}

			// --- Anti-trampa: pedir a mano un cofre que no esta pegado tiene que ser rechazado ---
			ctx.runOnClient(mc -> ClientPlayNetworking.send(new SwitchTabPayload(chestC)));
			ctx.waitTicks(20);
			check(ctx.computeOnClient(CosmosTabsClientTest::title).equals("Cofre A"),
					"el servidor rechaza abrir un cofre que no esta pegado", "titulo=" + ctx.computeOnClient(CosmosTabsClientTest::title));

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

			// --- Ctrl+clic + Ctrl+X en Cofre B, pestaña a Cofre A, Ctrl+V: las esmeraldas se mueven ---
			int[] w = window(ctx);
			input.holdControl();
			clickGui(ctx, input, w[0] + 8 + 8, w[1] + 18 + 8, InputConstants.MOUSE_BUTTON_LEFT);
			input.releaseControl();
			ctx.waitTicks(2);
			screenshot(ctx, "esmeraldas-marcadas");
			input.holdControl();
			input.pressKey(InputConstants.KEY_X);
			input.releaseControl();
			ctx.waitTicks(5);
			List<TabsPayload.Tab> groupTabs = ctx.computeOnClient(CosmosTabsClientTest::tabs);
			int idxA = indexOf(groupTabs, "Cofre A");
			clickGui(ctx, input, w[0] + idxA * (TAB_W + 1) + TAB_W / 2, w[1] - 10, InputConstants.MOUSE_BUTTON_LEFT);
			waitSoft(ctx, mc -> title(mc).equals("Cofre A"), 100);
			ctx.waitTicks(5);
			input.holdControl();
			input.pressKey(InputConstants.KEY_V);
			input.releaseControl();
			ctx.waitTicks(10);
			screenshot(ctx, "esmeraldas-pegadas-en-a");
			int[] counts = sp.getServer().computeOnServer(s -> {
				var level = s.overworld();
				return new int[] {countIn(level.getBlockEntity(chestA), Items.EMERALD), countIn(level.getBlockEntity(chestB), Items.EMERALD)};
			});
			check(counts[0] == 7 && counts[1] == 0, "Ctrl+X / Ctrl+V mueve las esmeraldas de B a A",
					"esmeraldas en A=" + counts[0] + " en B=" + counts[1]);

			// --- Dos ventanas: arrastrar la pestaña de Cofre B fuera de la barra (con Cofre A abierto) ---
			int[] wa = window(ctx);
			List<TabsPayload.Tab> tabsA = ctx.computeOnClient(CosmosTabsClientTest::tabs);
			int idxB = indexOf(tabsA, "Cofre B");
			int startX = wa[0] + idxB * (TAB_W + 1) + TAB_W / 2;
			int startY = wa[1] - 10;
			moveGui(ctx, input, startX, startY);
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTick();
			for (int i = 1; i <= 10; i++) {
				moveGui(ctx, input, startX + 20 * i, startY + 6 * i);
				ctx.waitTick();
			}
			screenshot(ctx, "arrastrando-pestana-b");
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			boolean dual = waitSoft(ctx, mc -> mc.gui.screen() instanceof DualContainerScreen, 100);
			ctx.waitTicks(10);
			screenshot(ctx, "dos-ventanas");
			String sideTitle = ctx.computeOnClient(mc -> mc.gui.screen() instanceof DualContainerScreen s
					? s.getMenu().sideTitle().getString() : "-");
			check(dual && sideTitle.equals("Cofre B") && ctx.computeOnClient(CosmosTabsClientTest::title).equals("Cofre A"),
					"arrastrar la pestaña afuera abre Cofre B en una segunda ventana",
					"doble=" + dual + " separada=" + sideTitle);

			if (dual) {
				// Arrastrar los diamantes de A (ranura 0) a la primera ranura de la ventana de B.
				int[] diamondSlot = slotOnScreen(ctx, 0);
				int[] sideSlot = slotOnScreen(ctx, ctx.computeOnClient(mc -> ((DualContainerScreen) mc.gui.screen()).getMenu().sideStart()));
				clickGui(ctx, input, diamondSlot[0], diamondSlot[1], InputConstants.MOUSE_BUTTON_LEFT);
				ctx.waitTicks(3);
				clickGui(ctx, input, sideSlot[0], sideSlot[1], InputConstants.MOUSE_BUTTON_LEFT);
				ctx.waitTicks(10);
				screenshot(ctx, "diamantes-a-la-otra-ventana");
				int[] diamonds = sp.getServer().computeOnServer(s -> {
					var level = s.overworld();
					return new int[] {countIn(level.getBlockEntity(chestA), Items.DIAMOND), countIn(level.getBlockEntity(chestB), Items.DIAMOND)};
				});
				check(diamonds[0] == 0 && diamonds[1] == 5, "se pueden pasar objetos de una ventana a la otra con el raton",
						"diamantes en A=" + diamonds[0] + " en B=" + diamonds[1]);

				// La x de la ventana separada la vuelve a juntar.
				int[] closeBtn = ctx.computeOnClient(mc -> {
					DualContainerScreen s = (DualContainerScreen) mc.gui.screen();
					Slot first = s.getMenu().slots.get(s.getMenu().sideStart());
					int[] offset = {((MovableWindow) s).cosmostabs$offsetX(), ((MovableWindow) s).cosmostabs$offsetY()};
					int left = ((ContainerScreenAccessor) s).cosmostabsTest$leftPos() + offset[0];
					int top = ((ContainerScreenAccessor) s).cosmostabsTest$topPos() + offset[1];
					// La ventana separada empieza 8 px a la izquierda y 18 px arriba de su primera ranura.
					return new int[] {left + first.x - 8 + 176 - 5, top + first.y - 18 - 5};
				});
				clickGui(ctx, input, closeBtn[0], closeBtn[1], InputConstants.MOUSE_BUTTON_LEFT);
				boolean single = waitSoft(ctx, mc -> mc.gui.screen() instanceof AbstractContainerScreen<?>
						&& !(mc.gui.screen() instanceof DualContainerScreen), 100);
				ctx.waitTicks(10);
				screenshot(ctx, "ventanas-juntadas");
				check(single && ctx.computeOnClient(CosmosTabsClientTest::title).equals("Cofre A"),
						"la x de la ventana separada vuelve a una sola ventana", "titulo=" + ctx.computeOnClient(CosmosTabsClientTest::title));
			}
			close(ctx, input);
		}

		dedicatedServer(ctx, input);

		if (failures.isEmpty()) {
			LOG.info("[cosmostabs-test] RESULTADO: TODO OK");
		} else {
			LOG.error("[cosmostabs-test] RESULTADO: {} fallo(s): {}", failures.size(), failures);
			throw new AssertionError("Cosmos Tabs: " + failures);
		}
	}

	/**
	 * Como en Crafty: servidor dedicado aparte y una pared de 12 cofres, mas pestañas de las que
	 * entran en la ventana. Comprueba las paginas y que se puedan separar dos ventanas.
	 */
	private void dedicatedServer(ClientGameTestContext ctx, TestInput input) {
		// Desde 26.x un servidor nuevo trae white-list=true: sin esto el jugador de prueba queda
		// afuera ("You are not white-listed") y la prueba falla sin probar nada del mod.
		Properties props = new Properties();
		props.setProperty("white-list", "false");
		props.setProperty("enforce-whitelist", "false");
		try (TestDedicatedServerContext server = ctx.worldBuilder().createServer(props);
				TestDedicatedServerConnection conn = server.connect()) {
			conn.waitForChunksRender();
			BlockPos p = server.computeOnServer(s -> s.getPlayerList().getPlayers().getFirst().blockPosition());
			cmd(server, "fill %s %s minecraft:air", p.offset(-4, 0, -4), p.offset(4, 3, 4));
			cmd(server, "fill %s %s minecraft:stone", p.offset(-4, -1, -4), p.offset(4, -1, 4));
			cmd(server, "tp @a %d %d %d -90 0", p.getX(), p.getY(), p.getZ());
			cmd(server, "time set noon");
			// Pared de 4 x 3 cofres sueltos delante del jugador (mirando a +X).
			BlockPos middle = null;
			int n = 0;
			for (int y = 2; y >= 0; y--) {
				for (int z = -2; z <= 1; z++) {
					BlockPos pos = p.offset(2, y, z);
					cmd(server, "setblock %s minecraft:chest[facing=west]{CustomName:\"Pared %d\"}", pos, ++n);
					if (y == 1 && z == 0) {
						middle = pos;
					}
				}
			}
			conn.waitForChunksRender();
			ctx.waitTicks(20);
			screenshot(ctx, "servidor-pared-de-cofres");

			open(ctx, input, middle, null);
			waitSoft(ctx, mc -> tabs(mc).size() >= 12, 100);
			List<TabsPayload.Tab> tabs = ctx.computeOnClient(CosmosTabsClientTest::tabs);
			screenshot(ctx, "servidor-pagina-actual");
			check(tabs.size() == 12, "servidor dedicado: la pared de 12 cofres tiene 12 pestañas", names(tabs));
			if (tabs.size() != 12) {
				close(ctx, input);
				return;
			}

			// 6 pestañas por pagina en un cofre (176 px): flechas justo despues de la sexta.
			int[] w = window(ctx);
			int arrowsX = w[0] + 6 * (TAB_W + 1);
			int current = -1;
			for (int i = 0; i < tabs.size(); i++) {
				if (tabs.get(i).current()) {
					current = i;
				}
			}
			// Ir a la pagina que no es la del cofre abierto y abrir su primera pestaña.
			boolean toNext = current < 6;
			clickGui(ctx, input, arrowsX + (toNext ? 16 : 5), w[1] - 10, InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(5);
			screenshot(ctx, "servidor-otra-pagina");
			int target = toNext ? 6 : 0;
			String targetName = tabs.get(target).name().getString();
			clickGui(ctx, input, w[0] + TAB_W / 2, w[1] - 10, InputConstants.MOUSE_BUTTON_LEFT);
			boolean switched = waitSoft(ctx, mc -> title(mc).equals(targetName), 100);
			ctx.waitTicks(10);
			screenshot(ctx, "servidor-cofre-de-otra-pagina");
			check(switched, "con las flechas se llega a " + targetName + " de la otra pagina",
					"titulo=" + ctx.computeOnClient(CosmosTabsClientTest::title));

			// Separar la pestaña de al lado (misma pagina que el cofre abierto) en otra ventana.
			int[] wa = window(ctx);
			String sideName = tabs.get(target + 1).name().getString();
			int startX = wa[0] + (TAB_W + 1) + TAB_W / 2;
			int startY = wa[1] - 10;
			moveGui(ctx, input, startX, startY);
			ctx.waitTick();
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTick();
			for (int i = 1; i <= 10; i++) {
				moveGui(ctx, input, startX + 20 * i, startY + 6 * i);
				ctx.waitTick();
			}
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			boolean dual = waitSoft(ctx, mc -> mc.gui.screen() instanceof DualContainerScreen, 100);
			ctx.waitTicks(10);
			screenshot(ctx, "servidor-dos-ventanas");
			String sideTitle = ctx.computeOnClient(mc -> mc.gui.screen() instanceof DualContainerScreen s
					? s.getMenu().sideTitle().getString() : "-");
			check(dual && sideTitle.equals(sideName), "servidor dedicado: arrastrar " + sideName + " afuera abre dos ventanas",
					"doble=" + dual + " separada=" + sideTitle + " pantalla="
							+ ctx.computeOnClient(mc -> mc.gui.screen() == null ? "ninguna" : mc.gui.screen().getClass().getSimpleName()));

			// Tapas: el recuento de cada 5 ticks tiene que ver la ventana doble, y al cerrar volver a 0.
			BlockPos mainPos = wallPos(p, target);
			BlockPos sidePos = wallPos(p, target + 1);
			ctx.waitTicks(20);
			int[] openWhile = server.computeOnServer(s -> new int[] {
					ChestBlockEntity.getOpenCount(s.overworld(), mainPos), ChestBlockEntity.getOpenCount(s.overworld(), sidePos)});
			check(openWhile[0] == 1 && openWhile[1] == 1, "con dos ventanas los dos cofres siguen abiertos (tapas)",
					"abiertos=" + openWhile[0] + "," + openWhile[1]);
			close(ctx, input);
			ctx.waitTicks(20);
			int[] openAfter = server.computeOnServer(s -> new int[] {
					ChestBlockEntity.getOpenCount(s.overworld(), mainPos), ChestBlockEntity.getOpenCount(s.overworld(), sidePos)});
			screenshot(ctx, "servidor-tapas-cerradas");
			check(openAfter[0] == 0 && openAfter[1] == 0, "al cerrar las dos ventanas las tapas se cierran (contador en 0)",
					"abiertos=" + openAfter[0] + "," + openAfter[1]);
		}
	}

	// ---------- ayudas ----------

	/** Posicion del cofre que ocupa el lugar {@code index} en las pestañas de la pared (arriba-abajo, luego por z). */
	private static BlockPos wallPos(BlockPos p, int index) {
		return p.offset(2, 2 - index / 4, -2 + index % 4);
	}

	private static void cmd(TestSingleplayerContext sp, String format, Object... args) {
		cmd(sp.getServer(), format, args);
	}

	private static void cmd(TestServerContext server, String format, Object... args) {
		Object[] formatted = new Object[args.length];
		for (int i = 0; i < args.length; i++) {
			formatted[i] = args[i] instanceof BlockPos b ? b.getX() + " " + b.getY() + " " + b.getZ() : args[i];
		}
		server.runCommand(String.format(format, formatted));
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

	/** Centro de la ranura {@code index} del menu abierto, en coordenadas de pantalla. */
	private static int[] slotOnScreen(ClientGameTestContext ctx, int index) {
		return ctx.computeOnClient(mc -> {
			AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.gui.screen();
			Slot slot = s.getMenu().slots.get(index);
			int left = ((ContainerScreenAccessor) s).cosmostabsTest$leftPos() + ((MovableWindow) s).cosmostabs$offsetX();
			int top = ((ContainerScreenAccessor) s).cosmostabsTest$topPos() + ((MovableWindow) s).cosmostabs$offsetY();
			return new int[] {left + slot.x + 8, top + slot.y + 8};
		});
	}

	private static int countIn(Object blockEntity, net.minecraft.world.item.Item item) {
		if (!(blockEntity instanceof net.minecraft.world.Container container)) {
			return -1;
		}
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
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
