# Cosmos Tabs

Mod de Fabric para **Minecraft 26.3** que convierte las ventanas de cofres en ventanas de verdad:

- **Ventana movible**: arrastra la barra de arriba para moverla; recuerda dónde la dejaste.
  Clic derecho en la barra la vuelve al centro.
- **Pestañas**: los contenedores **pegados** al que abriste (cofres, barriles, shulkers,
  dispensadores, tolvas, cofres de Ender…) aparecen como pestañas. Un clic cambia de cofre
  sin cerrar la ventana. Un cofre suelto no tiene pestañas.
- **Páginas de pestañas**: si el grupo tiene más pestañas de las que entran en la ventana (en
  un cofre, 6 por página), aparecen las flechas **◀ ▶** a la derecha de las pestañas; también
  se cambia de página con la **rueda del mouse** sobre la barra. Al abrir un cofre se muestra la
  página donde está. Un grupo puede tener hasta 64 contenedores.
- **Dos ventanas a la vez**: arrastra una pestaña fuera de la barra y ese cofre se abre en una
  segunda ventana flotante. Pasa objetos arrastrándolos de una a otra. La **x** (o soltarla
  sobre las pestañas) la vuelve a juntar.
- **Cortar y pegar**: **Ctrl+clic** marca ranuras (**Ctrl+A** todas), **Ctrl+X** las corta y
  **Ctrl+V** en otro cofre del grupo las mueve ahí.

Hornos, mesas de trabajo y demás estaciones no llevan pestañas, así conviven con mods que les
ponen su propia pantalla (por ejemplo Cinematic Interactions).

## Instalación

Necesita **Fabric Loader ≥ 0.20.1** y **Fabric API**. Descarga el `.jar` de
[Releases](../../releases/latest) y ponlo en la carpeta `mods`.

También viene incluido en el pack [Cosmos 26.3](https://github.com/YisHub/cosmos-minecraft-installer)
(instalador para Windows), que siempre trae la última versión.

- **Cliente y servidor**: para pestañas, dos ventanas y cortar/pegar, el mod tiene que estar en
  los dos. Con el mod solo en el cliente, la ventana se puede mover pero no hay pestañas.
- **Jugadores sin el mod** pueden entrar a un servidor que lo tiene: ven los cofres normales.
  El mod no registra bloques, objetos ni tipos de menú propios (la ventana doble viaja como un
  cofre grande vanilla y un paquete aparte le avisa al cliente con el mod), así que Fabric no
  pide el mod para conectarse.
- El servidor valida todo como si el jugador hiciera clic derecho en el bloque: alcance,
  protección del spawn y mods de protección (`UseBlockCallback`). Solo se puede saltar entre
  contenedores pegados.

## Compatibilidad y limitaciones

- **Otros mods de servidor:** la ventana doble se presenta al servidor como un "cofre grande"
  vanilla (`GENERIC_9x6`) aunque por dentro no es un `ChestMenu` ni tiene 54 + 36 ranuras. Es lo
  que permite entrar sin el mod. Un mod de servidor que dé por hecho que todo cofre grande es
  un `ChestMenu` podría fallar con la ventana doble abierta. Con Fabric API y Cinematic
  Interactions no pasa.
- **Tapas de los cofres:** el mod hace que el recuento de Minecraft (cada 5 ticks) reconozca la
  ventana doble, así que las tapas se abren y se cierran igual que en vanilla. Los cofres de
  Ender también, aunque ese caso no está en la prueba automática.
- Un grupo tiene como máximo **64 contenedores** (un cofre doble cuenta uno). Solo aparecen
  como pestañas los que están al alcance del jugador.

## Cómo está hecho

| Parte | Qué hace |
|---|---|
| `server/ContainerGroups` | Busca el grupo de contenedores pegados, cambia de pestaña, separa y junta ventanas. |
| `server/ItemClipboard` | Ctrl+X / Ctrl+V del lado del servidor. |
| `menu/DualContainerMenu` | La ventana doble: ranuras `[principal][inventario 36][separado]`. |
| `net/*Payload` | Paquetes propios (pestañas, cambiar, separar, juntar, cortar, pegar, aviso de ventana doble). |
| `mixin/ServerPlayerGameModeMixin` | Detecta qué bloque abrió el jugador con clic derecho. |
| `mixin/PlayerMixin` | Hace que el recuento de las tapas cuente la ventana doble. |
| `client/mixin/AbstractContainerScreenMixin` | Barra, pestañas, páginas, arrastrar, Ctrl+clic. |
| `client/mixin/ScreenMixin`, `MouseHandlerMixin` | Dibujan la ventana desplazada y corrigen el mouse. |
| `client/mixin/ClientPacketListenerMixin` | Evita que el mouse vuelva a la mira al cambiar de pestaña y abre la ventana doble. |
| `client/DualContainerScreen` | La pantalla de dos ventanas. |

## Desarrollo

Se compila con Java 25 (`./gradlew build`). Para no instalar nada en el sistema:

```sh
docker run --rm -u $(id -u):$(id -g) -e GRADLE_USER_HOME=/gradle -e HOME=/gradle \
  -v cosmos-tabs-gradle:/gradle -v "$PWD":/src -w /src eclipse-temurin:25-jdk ./gradlew build
```

### Pruebas

- **Compatibilidad** (`-Dcosmostabs.audit=true` o `./gradlew runClient -Paudit`): aplica todos
  los mixins de todos los mods cargados y se cierra. Los mods extra se ponen en `run-mods/`.
- **Prueba de punta a punta** (`src/gametest`, con
  [fabric-client-gametest](https://docs.fabricmc.net/develop/automatic-testing)): crea un mundo,
  coloca cofres, los abre, cambia de pestaña, arrastra ventanas, corta y pega, separa una
  pestaña… y saca capturas. `./gradlew testMods` deja en `build/test-mods` los tres jars para
  una instancia de prueba lanzada con `-Dfabric.client.gametest`.
  Con Prism: una instancia aparte (por ejemplo `26.3-test`) con esos jars y los demás mods del
  pack, y `-Dfabric.client.gametest -Dcosmostabs.debug=true` en sus argumentos de Java. Las
  capturas quedan en su carpeta `screenshots/` y cada comprobación escribe
  `[cosmostabs-test] PASS/FAIL` en el log; al final, `RESULTADO: TODO OK` o la lista de fallos.
  La segunda parte arranca un servidor dedicado, que necesita `eula=true` en el `eula.txt` de
  esa instancia.
  La segunda parte arranca un **servidor dedicado** (como Crafty) con una pared de 12 cofres.
- **Lista blanca:** desde Minecraft 26.x todo servidor nuevo se crea con `white-list=true`, y
  quien no está en la lista queda afuera con *"You are not white-listed on this server!"* antes
  de llegar a probar nada del mod. La prueba automática ya arranca su servidor con
  `white-list=false`; para pruebas a mano (servidor en Docker, Crafty, jugador sin el mod)
  pon `white-list=false` en `server.properties` antes de arrancarlo, o usa
  `/whitelist add <nombre>`. Si alguien no puede entrar, mira primero el motivo exacto en el
  log del servidor: la lista blanca y el aviso *"requires Fabric API…"* son problemas distintos.
- `-Dcosmostabs.debug=true` explica en el log cada clic en una pestaña y por qué el servidor lo
  acepta o lo rechaza.

## Cambios

Ver [CHANGELOG.md](CHANGELOG.md).

## Publicar una versión

Sube `version` en `gradle.properties` y crea un tag: `git tag v1.0.1 && git push --tags`.
GitHub Actions compila y publica el `.jar` en Releases.

## Licencia

MIT
