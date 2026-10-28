# Cosmos Tabs

Mod de Fabric para **Minecraft 26.3** que convierte las ventanas de cofres en ventanas de verdad:

- **Ventana movible**: arrastra la barra de arriba para moverla; recuerda dónde la dejaste.
  Clic derecho en la barra la vuelve al centro.
- **Pestañas**: los contenedores **pegados** al que abriste (cofres, barriles, shulkers,
  dispensadores, tolvas, cofres de Ender…) aparecen como pestañas. Un clic cambia de cofre
  sin cerrar la ventana. Un cofre suelto no tiene pestañas.
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

- **Cliente y servidor**: para pestañas, dos ventanas y cortar/pegar, el mod tiene que estar en
  los dos. Con el mod solo en el cliente, la ventana se puede mover pero no hay pestañas.
- El servidor valida todo como si el jugador hiciera clic derecho en el bloque: alcance,
  protección del spawn y mods de protección (`UseBlockCallback`). Solo se puede saltar entre
  contenedores pegados.

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
- `-Dcosmostabs.debug=true` explica en el log cada clic en una pestaña y por qué el servidor lo
  acepta o lo rechaza.

## Publicar una versión

Sube `version` en `gradle.properties` y crea un tag: `git tag v1.0.1 && git push --tags`.
GitHub Actions compila y publica el `.jar` en Releases.

## Licencia

MIT
