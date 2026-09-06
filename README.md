# 🎯 Quiz Multijugador

Juego web multijugador de preguntas para niños (10+ años, pensado también para más pequeños).
Hecho con **Java 21 + Quarkus + PostgreSQL**, API **REST**, tiempo real por **WebSockets** y un
frontend sencillo (**HTML + CSS + JavaScript vanilla + Bootstrap**) sin frameworks.

---

## ✨ Reglas del juego (MVP)

- Un jugador **crea** una partida (5, 10, 15 o **25** preguntas por jugador) y comparte el **código de sala**; los demás se **unen** con ese código y su apodo.
- Categorías: **FÚTBOL** (con historia del fútbol), **MATEMÁTICAS**, **CIENCIA**, **HISTORIA**, **CULTURA GENERAL**, **GEOGRAFÍA**, **ANIMALES**, **TECNOLOGÍA** y **PELÍCULAS** (Disney infantiles y películas famosas).
- Cada ronda el servidor elige **una categoría al azar** entre las disponibles y **una pregunta al azar** dentro de ella.
- **Nunca se repite una pregunta dentro de la misma partida.** Si una categoría se queda sin preguntas se descarta temporalmente.
- Cada pregunta dura **15 segundos**; **el servidor es la autoridad del tiempo** (el contador del navegador es solo informativo).
- Puntuación:
  - Respuesta correcta: **100 puntos**.
  - **Bonus por rapidez:** `redondeo(20 × tiempoRestanteMs / 15000)` → de **0 a 20** puntos extra.
  - Incorrecta o sin respuesta: **0 puntos**. No hay penalización.
- Al final se muestra el **ranking** ordenado por puntos.

### 🎮 Dos modos de juego

Al crear una partida se elige la modalidad (`mode`, ver [API](#-api-rest)):

- **`ONLINE` (En línea):** cada jugador usa su propio dispositivo; todos
  responden la **misma pregunta a la vez** y el resultado de la ronda llega
  cuando todos respondieron o se acaba el tiempo.
- **`LOCAL` (Misma pantalla):** los jugadores comparten **una sola pantalla**
  y en cada **ronda (ciclo) juegan TODOS por turnos**: ronda 1 → jugador 1,
  luego jugador 2, luego jugador 3 (cada uno responde **su propia pregunta**,
  distinta y aleatoria); ronda 2 → otra vez los 3 por turnos, etc. Con N
  rondas configuradas cada jugador responde **exactamente N preguntas**
  (N × jugadores en total, sin repetir ninguna). El sistema **anuncia de
  quién es el turno** en cada pregunta (evento `NEW_QUESTION` con los campos
  `player`, `cycle` y `turnOrder`) y los 3 jugadores se ven siempre en
  pantalla con su nombre, avatar y puntos. Al seleccionar una respuesta se
  envía y se **califica al instante** (correcta/incorrecta con puntos y
  bonus); solo puede responder el jugador al que le toca. Pasan el
  dispositivo cuando el sistema diga el turno.

> 💡 Si la base de datos tiene menos preguntas únicas que las rondas pedidas (p. ej. 8 preguntas
> para una partida de 10), la partida **no duplica preguntas**: juega las que haya y termina
> informando que no hay suficientes preguntas.

---

## 🧰 Requisitos

- **Java 21** (LTS)
- **Maven** 3.9+
- **Docker** + **Docker Compose** v2 (para la opción con contenedores)
- Navegador moderno (Chrome, Firefox, Edge)

---

## 🚀 Ejecución

### Opción A — Todo con Docker Compose (recomendada)

PostgreSQL y la aplicación corren **juntos** en la misma red de Docker:

```bash
cd /home/miltonahdz/Descargas/quiz-multijugador
docker compose up -d --build
```

- App: <http://localhost:8080>
- PostgreSQL: `localhost:5433` (usuario/BD `quiz`; el 5432 local puede estar ocupado por otro PostgreSQL, por eso se publica en 5433). Internamente la app se conecta al servicio `db:5432`.
- Las preguntas se guardan en el volumen `db-data` (persistente).
- Las partidas activas viven **en memoria** de la app (no se persisten).

Comandos útiles:

```bash
docker compose ps            # estado
docker compose logs -f app   # logs de la app
docker compose down          # detener (conserva la BD)
docker compose down -v       # detener y borrar la BD (empezar de cero)
```

### Opción B — Desarrollo local (sin contenedores para la app)

```bash
docker compose up -d db               # solo PostgreSQL (puerto 5433 del host)
mvn quarkus:dev                       # Quarkus en modo dev -> http://localhost:8080
```

> En desarrollo, la app por defecto busca PostgreSQL en `localhost:5432` (ver `src/main/resources/application.properties`).
> Si usas el PostgreSQL de Compose (host 5433), pásale la URL:
> `mvn quarkus:dev -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5433/quiz`

---

## 🧪 Tests

```bash
mvn test
```

La suite es rápida y no requiere Docker: los tests usan **H2 en memoria** (mismo esquema vía Flyway)
y dobles de temporizador/reloj para no esperar 15 segundos reales.

| Área | Test | Qué cubre |
|---|---|---|
| Creación/unión | `GameServiceTest` | crear partida (5/10/15), unir jugadores, validaciones |
| Selección aleatoria | `GameRandomSelectionTest` | categoría y pregunta aleatorias, sin repetición, categorías agotadas, límites |
| Rondas | `GameRoundsTest` | inicio, pregunta nueva, respuestas, resultado, siguiente ronda, desconexión |
| Tiempo y puntos | `GameTimerScoringTest` | cierre a los 15 s, rechazo de respuestas tardías, bonus por rapidez |
| Flujo completo | `FullGameFlowTest` | 3 jugadores × 5 rondas hasta el ranking y la limpieza |
| Misma pantalla | `LocalTurnModeTest` | cada ronda la juegan todos por turnos, rechazo fuera de turno, calificación inmediata, timeout, regresión ONLINE |
| WebSocket | `SessionRegistryTest` | envío asíncrono, sesiones rotas/cerradas sin bloquear el servidor |
| REST | `GameResourceTest` | endpoints HTTP, modos ONLINE/LOCAL, errores 400/404, sin fuga de `correctOption` |
| BD | `QuestionRepositoryTest` | migraciones Flyway (V1–V6), 9 categorías (PELÍCULAS con 25 y el resto con 35), EASY/MEDIUM/HARD, sin textos duplicados |

---

## 🗂️ Estructura del proyecto

```
quiz-multijugador/
├── compose.yaml                  # PostgreSQL + app juntos (red de Docker)
├── Dockerfile                    # imagen multi-etapa de la app
├── .dockerignore
├── pom.xml
├── src/main/java/com/example/quiz/
│   ├── question/
│   │   ├── Question.java         # entidad JPA (persistida)
│   │   ├── QuestionRepository.java  # Panache + consultas de bolsas aleatorias
│   │   └── QuestionResource.java    # GET /api/questions/categories
│   ├── game/
│   │   ├── GameSession.java      # estado de una partida (en memoria)
│   │   ├── Player.java           # jugador en memoria
│   │   ├── GameMode.java         # ONLINE (dispositivos) | LOCAL (misma pantalla)
│   │   ├── GameService.java      # orquestador: rondas, tiempo, puntos, eventos
│   │   ├── GameResource.java     # REST de partidas
│   │   ├── GameEvents.java / WsMessage.java / GameEventBroadcaster.java
│   │   ├── GameStatus.java / GameNotFoundException.java
│   │   ├── TimeProvider.java / SystemTimeProvider.java
│   │   ├── RoundScheduler.java / VertxRoundScheduler.java
│   │   └── dto/                  # DTOs públicos (sin correctOption)
│   └── websocket/
│       ├── GameWebSocket.java    # @ServerEndpoint /ws/game
│       └── SessionRegistry.java  # conexiones por partida + broadcast
├── src/main/resources/
│   ├── application.properties    # datasource, Flyway, perfil %test (H2)
│   ├── db/migration/V1__create_questions.sql
│   ├── db/migration/V2__insert_initial_questions.sql
│   ├── db/migration/V3__add_more_questions.sql   # 10 preguntas EASY por categoría
│   ├── db/migration/V4__add_medium_questions.sql # +10 MEDIUM por categoría (Fútbol = historia)
│   ├── db/migration/V5__add_hard_questions.sql   # +5 HARD por categoría (Fútbol = historia)
│   ├── db/migration/V6__movies_and_more_questions.sql # PELÍCULAS (25) + 10 por categoría
│   └── META-INF/resources/       # frontend (index.html, css/, js/)
└── src/test/java/...             # tests unitarios + @QuarkusTest
```

> Decisión de diseño: `Player` y `GameSession` viven en `game/` (no en `player/`) porque **no se
> persisten** y así se evita un paquete de una sola clase.

---

## 🔌 API REST

Base: `http://localhost:8080`

| Método | Ruta | Descripción | Respuestas |
|---|---|---|---|
| `POST` | `/api/games` | Crea una partida. Cuerpo `{"totalRounds": 5\|10\|15, "mode": "ONLINE"\|"LOCAL"}` (`mode` opcional; por defecto `ONLINE`) | `201` → `GameDto` · `400` |
| `POST` | `/api/games/{gameId}/players` | Añade jugador. Cuerpo `{"nickname": "Ana"}` | `201` → `PlayerDto` · `400/404` |
| `GET` | `/api/games/{gameId}` | Información pública de la partida | `200` → `GameDto` · `404` |
| `GET` | `/api/questions/categories` | Categorías con preguntas activas | `200` → `["ANIMALES", …]` |

**Errores:** cuerpo JSON `{"message": "..."}` con `400` (validación) o `404` (partida inexistente).

**Seguridad:** ningún DTO/REST expone `correctOption` ni la pregunta de la ronda actual.

---

## ⚡ WebSocket

Endpoint: `ws://localhost:8080/ws/game`

Mensajes **entrantes** (el cliente):

```json
{ "type": "JOIN",        "gameId": "ABC23", "playerId": "uuid" }
{ "type": "START_GAME" }
{ "type": "ANSWER",      "option": "B" }                                   // modo ONLINE
{ "type": "ANSWER",      "option": "B", "playerId": "uuid-del-turno" }     // modo LOCAL
```

> En **ONLINE** la respuesta se atribuye al jugador enlazado a la sesión (el
> `playerId` se ignora). En **LOCAL** el mensaje indica qué jugador responde
> y el servidor exige que sea el del turno anunciado.

Eventos **salientes** (formato `{"type", "gameId", "payload"}`):

| Evento | Momento |
|---|---|
| `GAME_STARTED` | la partida empieza (`players`, `totalRounds` y `mode`) |
| `NEW_QUESTION` | nueva pregunta: `round, category, questionId, question, optionA..D, timeLimitMs` — **sin `correctOption`**; en modo `LOCAL` incluye además `player: {playerId, nickname}`, `cycle` (ronda visible) y `turnOrder` (turno dentro de la ronda) |
| `PLAYER_ANSWERED` | un jugador respondió (avance `answeredCount/totalPlayers`) |
| `QUESTION_RESULT` | fin de la ronda: **aquí sí** se revela `correctOption`, con aciertos, puntos y scores |
| `NEXT_QUESTION` | transición a la siguiente ronda |
| `GAME_FINISHED` | ranking final (y aviso si faltaron preguntas) |
| `PLAYER_JOINED` / `PLAYER_LEFT` | la sala cambia (lista de jugadores) |
| `ERROR` | mensaje de error puntual para un cliente |

---

## 🏗️ Arquitectura

- **Solo se persisten las preguntas** (PostgreSQL + Flyway + Hibernate ORM con Panache).
- **Las partidas activas viven en memoria**: `ConcurrentHashMap<String, GameSession>` en `GameService`
  (jugadores, rondas, ids usados, respuestas, tiempos y puntuaciones). Al terminar, la partida se elimina.
- **El servidor es la única autoridad**: conoce la respuesta correcta, mide el tiempo (reloj del
  servidor) y valida respuestas y puntos. El navegador solo muestra y envía intenciones.
- **Cierre por tiempo**: al emitir `NEW_QUESTION` se programa un temporizador Vert.x de 15 s
  (`RoundScheduler`); si todos responden antes, se cancela. Los disparos obsoletos se ignoran.
- **Broadcaster desacoplado**: `GameService` emite eventos por `GameEventBroadcaster`
  (implementado por `SessionRegistry`), lo que permite testear las rondas sin WebSockets.
- **Escalado futuro**: el estado en memoria asume una sola instancia. Para varias instancias se
  migraría el estado a **Redis** (documentado como evolución; fuera del MVP).

---

## ➕ Cómo agregar nuevas preguntas

1. Crea una migración nueva `src/main/resources/db/migration/V3__add_more_questions.sql` (nunca edites las V1/V2 ya aplicadas).
2. Insert filas con el mismo formato (la columna `correct_option` guarda la **letra** A–D):

```sql
INSERT INTO questions (category, question, option_a, option_b, option_c, option_d, correct_option, difficulty, active)
VALUES ('CIENCIA', '¿Cuál es el planeta más grande del sistema solar?',
        'Júpiter', 'Saturno', 'Neptuno', 'Urano', 'A', 'EASY', TRUE);
```

3. Reinicia la app (con Docker: `docker compose up -d --build`); Flyway aplica la migración al arrancar.
4. La categoría aparecerá automáticamente y las bolsas aleatorias la usarán (funciona con 1 o con cientos de preguntas por categoría).
5. Dificultades soportadas como texto: `EASY`, `MEDIUM`, `HARD` (el banco mezcla EASY y MEDIUM; la dificultad aún no filtra en el MVP).

---

## 🔒 Seguridad de la respuesta correcta

`correctOption` se guarda en la BD y solo lo conoce el servidor. `NEW_QUESTION` nunca lo incluye;
el cliente recibe solo `id, category, question, optionA..D, timeLimitMs`. La respuesta correcta se
revela únicamente en `QUESTION_RESULT` (una vez cerrada la ronda).

---

## 🐙 Crear un repositorio remoto de GitHub (futuro)

Este proyecto trabaja **solo con Git local** por ahora. Cuando quieras publicarlo:

```bash
cd /home/miltonahdz/Descargas/quiz-multijugador
git remote add origin https://github.com/TU_USUARIO/quiz-multijugador.git
git branch -M main
git push -u origin main
```

> ⚠️ Solo cuando tú lo decidas: no se configura ni se hace `push` en este proyecto sin tu indicación.

---

## 📄 Configuración relevante

`src/main/resources/application.properties`:

- `quarkus.datasource.*` — PostgreSQL (dev/prod). En Docker se sobrescribe por variables de entorno de `compose.yaml`.
- `quarkus.flyway.migrate-at-start=true` — aplica migraciones al arrancar.
- `quarkus.hibernate-orm.database.generation=none` — el esquema lo gestiona Flyway.
- Perfil `%test.*` — H2 en memoria (mismo esquema vía Flyway) para ejecutar `mvn test` sin Docker.
