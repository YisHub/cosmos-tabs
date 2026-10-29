# Cambios

## 1.0.1

- **Pueden entrar jugadores sin el mod.** La 1.0.0 registraba un tipo de menú propio y Fabric
  echaba del servidor a quien no tuviera el mod ("This server requires Fabric API…
  cosmostabs"). Ahora la ventana doble viaja como un cofre grande vanilla y el cliente con el
  mod la reconoce por un aviso aparte (`DualOpenPayload`).
- **Páginas de pestañas.** Con más contenedores de los que entran en la ventana, las pestañas
  sobrantes se dibujaban fuera de ella y no respondían a los clics (en una pared de 9 cofres no
  se podía llegar al noveno). Ahora se reparten en páginas con flechas ◀ ▶ y rueda del mouse.
- El servidor ya no corta el grupo en 9 contenedores (tope: 64).
- **Tapas de cofres que quedaban abiertas.** Cada 5 ticks Minecraft recuenta quién tiene abierto
  un cofre y no reconocía la ventana doble: el contador quedaba en -1 al cerrarla y desde ahí la
  animación de la tapa quedaba desfasada. Ahora el recuento cuenta también la ventana doble.
  También vale para cofres de Ender separados en la segunda ventana (sin prueba automática).
  Los cofres que quedaron con la tapa abierta se arreglan al abrirlos y cerrarlos de nuevo.
- Más robusto: el aviso de ventana doble se descarta al desconectarse y se ignora si no
  corresponde a un cofre grande o trae tamaños imposibles; si el cliente no admite dos
  ventanas, el servidor avisa antes de tocar los cofres (no genera su botín en vano).
- El tope de 64 pestañas cuenta contenedores, no bloques (un cofre doble ya no cuenta dos).
- Páginas: el hueco de una última página incompleta ya no mueve la ventana, y la página
  elegida no salta si el servidor reenvía las pestañas.
- La prueba automática también corre contra un **servidor dedicado** (pared de 12 cofres:
  páginas y dos ventanas), además del mundo de un jugador.

## 1.0.0

- Ventanas de contenedores movibles, pestañas entre contenedores pegados, dos ventanas a la vez
  y cortar/pegar con Ctrl+clic, Ctrl+A, Ctrl+X y Ctrl+V.
