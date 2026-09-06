package com.example.quiz.game;

import com.example.quiz.game.dto.PlayerDto;
import com.example.quiz.question.Question;
import com.example.quiz.question.QuestionRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ciclo de vida de las partidas activas. Singleton en memoria: un
 * {@code Map<String, GameSession>} con el estado de todos los juegos.
 *
 * <p>FASE 3: crear partidas y unir jugadores. FASE 4: selección aleatoria de
 * preguntas sin repetición. FASE 6: orquestación de rondas por WebSocket.
 * FASE 7: el servidor es la autoridad del tiempo (cierre automático a los
 * 15 s, rechazo de respuestas tardías) y de la puntuación. EXTENSIÓN LOCAL:
 * modalidad de misma pantalla por turnos ({@link GameMode#LOCAL}), donde
 * cada ronda la juega un solo jugador y se califica al instante.
 *
 * <p>Puntuación (FASE 7, documentada): respuesta correcta = 100 puntos;
 * bonus por rapidez = redondeo de {@code 20 × remainingMs / 15000} (de 0 a
 * 20, según el tiempo que quedaba al responder); incorrecta o sin respuesta
 * = 0 puntos. Sin penalización.
 *
 * <p>Toda la mutación de una misma partida se serializa con
 * {@code synchronized(session)} (reentrante). Los eventos salen por
 * {@link GameEventBroadcaster}; los temporizadores por {@link RoundScheduler}
 * y el reloj por {@link TimeProvider} (ambos inyectables para testear).
 *
 * <p>Diseño: los juegos no se persisten; en una versión futura este estado
 * podría migrar a Redis si se necesitan varias instancias del backend.
 */
@ApplicationScoped
public class GameService {

    /** Cantidades de preguntas permitidas por partida. */
    public static final List<Integer> ALLOWED_TOTAL_ROUNDS = List.of(5, 10, 15);

    /** Longitud máxima del apodo. */
    public static final int MAX_NICKNAME_LENGTH = 20;

    /** Alfabeto del gameId sin caracteres ambiguos (0, O, 1, I). */
    private static final String GAME_ID_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int GAME_ID_LENGTH = 5;

    /** Tiempo límite por pregunta en milisegundos (autoridad del servidor). */
    public static final long QUESTION_TIME_LIMIT_MS = 15_000;

    /** Puntos por respuesta correcta. */
    public static final int POINTS_CORRECT_ANSWER = 100;

    /** Bonus máximo por rapidez (0..{@value}). */
    public static final int MAX_SPEED_BONUS = 20;

    private final Map<String, GameSession> games = new ConcurrentHashMap<>();
    private final QuestionRepository questionRepository;
    private final Random random;
    private final GameEventBroadcaster broadcaster;
    private final RoundScheduler roundScheduler;
    private final TimeProvider timeProvider;

    /** Constructor usado por CDI (aleatoriedad real, WebSocket, Vert.x, reloj real). */
    @Inject
    public GameService(QuestionRepository questionRepository, GameEventBroadcaster broadcaster,
                       RoundScheduler roundScheduler, TimeProvider timeProvider) {
        this(questionRepository, new Random(), broadcaster, roundScheduler, timeProvider);
    }

    /** Constructor maestro: visible para tests (semilla, reloj y timer controlables). */
    GameService(QuestionRepository questionRepository, Random random, GameEventBroadcaster broadcaster,
                RoundScheduler roundScheduler, TimeProvider timeProvider) {
        this.questionRepository = questionRepository;
        this.random = random;
        this.broadcaster = broadcaster;
        this.roundScheduler = roundScheduler;
        this.timeProvider = timeProvider;
    }

    /** Visible para tests de rondas FASE 6 (sin temporizador automático). */
    GameService(QuestionRepository questionRepository, Random random, GameEventBroadcaster broadcaster) {
        this(questionRepository, random, broadcaster, null, System::currentTimeMillis);
    }

    /** Visible para tests de FASE 3/4 (sin broadcaster: sin eventos). */
    GameService(QuestionRepository questionRepository, Random random) {
        this(questionRepository, random, null, null, System::currentTimeMillis);
    }

    /** Visible para tests de FASE 3 (sin broadcaster y con aleatoriedad real). */
    GameService(QuestionRepository questionRepository) {
        this(questionRepository, new Random(), null, null, System::currentTimeMillis);
    }

    // ====================== Sala de espera (FASE 3) ======================

    /**
     * Crea una partida nueva en estado LOBBY (modalidad ONLINE por defecto).
     *
     * @param totalRounds cantidad de preguntas: debe ser 5, 10 o 15
     * @return la sesión creada
     * @throws IllegalArgumentException si totalRounds no está permitido
     */
    public GameSession createGame(int totalRounds) {
        return createGame(totalRounds, GameMode.ONLINE);
    }

    /**
     * Crea una partida nueva indicando la modalidad como texto.
     *
     * @param mode "ONLINE" (cada quien en su dispositivo) o "LOCAL" (misma
     *             pantalla, por turnos); {@code null} = ONLINE
     */
    public GameSession createGame(int totalRounds, String mode) {
        return createGame(totalRounds, GameMode.parse(mode));
    }

    /**
     * Crea una partida nueva en estado LOBBY con la modalidad indicada.
     */
    public GameSession createGame(int totalRounds, GameMode mode) {
        if (!ALLOWED_TOTAL_ROUNDS.contains(totalRounds)) {
            throw new IllegalArgumentException(
                    "Cantidad de preguntas inválida (" + totalRounds
                            + "). Permitidas: " + ALLOWED_TOTAL_ROUNDS);
        }
        GameSession session = new GameSession(generateUniqueGameId(), totalRounds,
                mode == null ? GameMode.ONLINE : mode);
        games.put(session.gameId, session);
        return session;
    }

    /**
     * Añade un jugador a la partida (estado LOBBY).
     *
     * @param gameId   identificador de la partida (no distingue mayúsculas)
     * @param nickname apodo visible, de 1 a {@value #MAX_NICKNAME_LENGTH} caracteres
     * @return el jugador creado, con score 0 y conectado
     * @throws GameNotFoundException si la partida no existe
     * @throws IllegalArgumentException si ya no admite jugadores o el apodo
     *                                  no es válido/está repetido
     */
    public Player joinGame(String gameId, String nickname) {
        GameSession session = games.get(normalizeGameId(gameId));
        if (session == null) {
            throw new GameNotFoundException(gameId);
        }
        synchronized (session) {
            if (session.status != GameStatus.LOBBY) {
                throw new IllegalArgumentException("La partida ya no acepta jugadores");
            }
            String cleanNickname = normalizeNickname(nickname);
            boolean nicknameTaken = session.players.stream()
                    .anyMatch(p -> p.nickname.equalsIgnoreCase(cleanNickname));
            if (nicknameTaken) {
                throw new IllegalArgumentException(
                        "El apodo '" + cleanNickname + "' ya está en uso en esta partida");
            }
            Player player = new Player(UUID.randomUUID().toString(), cleanNickname);
            session.players.add(player);
            return player;
        }
    }

    /**
     * Devuelve la sesión de una partida, si existe (solo lectura).
     */
    public Optional<GameSession> findGame(String gameId) {
        if (gameId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(games.get(normalizeGameId(gameId)));
    }

    // ====================== WebSocket (FASE 6) ======================

    /**
     * Valida que el jugador pertenezca a la partida y devuelve el gameId
     * canónico (mayúsculas). Lo usa el WebSocket antes de enlazar la sesión.
     *
     * @throws GameNotFoundException si la partida no existe
     * @throws IllegalArgumentException si el jugador no está en la partida
     */
    public String validatePlayerInGame(String gameId, String playerId) {
        GameSession session = games.get(normalizeGameId(gameId));
        if (session == null) {
            throw new GameNotFoundException(gameId);
        }
        synchronized (session) {
            findPlayer(session, playerId);
        }
        return session.gameId;
    }

    /**
     * Marca conectado al jugador (re)conectado por WebSocket y anuncia la
     * lista actual de jugadores en la sala.
     */
    public void onPlayerReconnected(String gameId, String playerId) {
        GameSession session = requireGame(gameId);
        synchronized (session) {
            findPlayer(session, playerId).connected = true;
            broadcast(session, GameEvents.PLAYER_JOINED, Map.of("players", playersPayload(session)));
        }
    }

    /**
     * Marca desconectado a un jugador cuando cierra su WebSocket.
     */
    public void playerLeft(String gameId, String playerId) {
        GameSession session = games.get(normalizeGameId(gameId));
        if (session == null) {
            return; // la partida ya no existe (terminó)
        }
        synchronized (session) {
            Player player = findPlayerOptional(session, playerId);
            if (player == null) {
                return;
            }
            player.connected = false;
            player.answeredCurrentQuestion = false;
            broadcast(session, GameEvents.PLAYER_LEFT,
                    Map.of("playerId", player.playerId, "players", playersPayload(session)));
        }
    }

    /**
     * Inicia la partida: pasa a IN_PROGRESS y dispara la primera ronda.
     * Cualquier jugador de la partida puede iniciarla (MVP, sin rol de anfitrión).
     *
     * @throws GameNotFoundException si la partida no existe
     * @throws IllegalArgumentException si no está en LOBBY o el jugador no es
     *                                  parte de la partida
     */
    public void startGame(String gameId, String playerId) {
        GameSession session = requireGame(gameId);
        synchronized (session) {
            if (session.status != GameStatus.LOBBY) {
                throw new IllegalArgumentException("La partida no está en la sala de espera");
            }
            findPlayer(session, playerId); // solo jugadores de la partida pueden iniciarla
            session.status = GameStatus.IN_PROGRESS;
            broadcast(session, GameEvents.GAME_STARTED, Map.of(
                    "totalRounds", session.totalRounds,
                    "mode", session.mode.name(),
                    "players", playersPayload(session)));
            beginNextRound(session);
        }
    }

    /**
     * Registra la respuesta de un jugador a la pregunta de la ronda en curso.
     * Versión usada por los tests: en modo ONLINE el jugador es quien responde;
     * en modo LOCAL debe coincidir con el jugador al que le toca el turno.
     */
    public void submitAnswer(String gameId, String playerId, String option) {
        submitAnswer(gameId, playerId, playerId, option);
    }

    /**
     * Registra la respuesta de un jugador a la pregunta de la ronda en curso.
     *
     * <p>El servidor es la autoridad del tiempo: si la pregunta ya venció (15 s)
     * la respuesta se rechaza aunque el temporizador aún no haya disparado.
     * Cuando todos los jugadores conectados han respondido, la ronda se cierra
     * automáticamente (resultado y, si quedan rondas, siguiente pregunta).
     *
     * <p>Identidad del que responde:
     * <ul>
     *   <li><b>ONLINE</b>: siempre el jugador enlazado a la sesión
     *       ({@code boundPlayerId}); el campo {@code declaredPlayerId} se ignora.</li>
     *   <li><b>LOCAL</b> (misma pantalla): se usa {@code declaredPlayerId} y el
     *       servidor exige que sea el jugador al que le toca el turno. Al
     *       responder, la ronda se cierra y se califica al instante.</li>
     * </ul>
     *
     * @param option opción elegida: "A", "B", "C" o "D"
     * @throws GameNotFoundException si la partida no existe
     * @throws IllegalArgumentException si no hay pregunta activa, el jugador
     *                                  no está en la partida/no es su turno, la
     *                                  opción es inválida o el tiempo terminó
     */
    public void submitAnswer(String gameId, String boundPlayerId, String declaredPlayerId, String option) {
        GameSession session = requireGame(gameId);
        synchronized (session) {
            if (session.mode == GameMode.LOCAL) {
                submitTurnAnswerLocked(session, declaredPlayerId, option);
            } else {
                submitOnlineAnswerLocked(session, boundPlayerId, option);
            }
        }
    }

    /** Respuesta del modo ONLINE: vale la de cualquier jugador conectado. */
    private void submitOnlineAnswerLocked(GameSession session, String playerId, String option) {
        if (session.status != GameStatus.IN_PROGRESS || session.currentQuestion == null) {
            throw new IllegalArgumentException("No hay una pregunta activa en este momento");
        }
        Player player = findPlayer(session, playerId);
        boolean recorded = recordAnswer(session, player, option);
        if (recorded && countConnected(session) > 0
                && countAnswered(session) == countConnected(session)) {
            closeRoundAndAdvance(session);
        }
    }

    /** Respuesta del modo LOCAL: solo vale la del jugador al que le toca. */
    private void submitTurnAnswerLocked(GameSession session, String playerId, String option) {
        if (session.status != GameStatus.IN_PROGRESS || session.currentQuestion == null) {
            throw new IllegalArgumentException("No hay una pregunta activa en este momento");
        }
        Player turn = currentTurnPlayer(session);
        if (turn == null) {
            throw new IllegalStateException("La partida no tiene jugadores para repartir turnos");
        }
        if (playerId == null || !turn.playerId.equals(playerId)) {
            throw new IllegalArgumentException(
                    "No es el turno de ese jugador: ahora le toca a " + turn.nickname);
        }
        if (recordAnswer(session, turn, option)) {
            // En modo LOCAL cada ronda la juega un solo jugador: al responder,
            // la ronda se cierra y se califica al instante (correcta/incorrecta).
            closeRoundAndAdvance(session);
        }
    }

    /**
     * Valida la opción y el tiempo y registra la respuesta del jugador
     * (marca, guarda, emite PLAYER_ANSWERED). Devuelve false si el jugador
     * ya había respondido (duplicado que se ignora).
     */
    private boolean recordAnswer(GameSession session, Player player, String option) {
        if (option == null || !option.matches("[A-D]")) {
            throw new IllegalArgumentException(
                    "Opción inválida: '" + option + "' (se espera A, B, C o D)");
        }
        if (player.answeredCurrentQuestion) {
            return false; // respuestas duplicadas se ignoran
        }
        long nowMs = timeProvider.currentTimeMillis();
        if (nowMs - session.questionStartTimeMs >= QUESTION_TIME_LIMIT_MS) {
            throw new IllegalArgumentException("El tiempo de esta pregunta terminó");
        }
        player.answeredCurrentQuestion = true;
        session.currentRoundAnswers.put(player.playerId, option);
        session.answerTimesMs.put(player.playerId, nowMs);

        broadcast(session, GameEvents.PLAYER_ANSWERED, Map.of(
                "playerId", player.playerId,
                "nickname", player.nickname,
                "answeredCount", countAnswered(session),
                "totalPlayers", countConnected(session)));
        return true;
    }

    /**
     * Invocado por el temporizador de 15 s de una ronda. Cierra la ronda si
     * sigue abierta (nadie respondió o faltan respuestas). Un disparo obsoleto
     * (de una ronda ya cerrada) se ignora comparando el instante de inicio.
     */
    void onRoundTimeout(String gameId, long expectedStartMs) {
        GameSession session = games.get(normalizeGameId(gameId));
        if (session == null) {
            return; // la partida ya terminó y se eliminó
        }
        synchronized (session) {
            if (session.status != GameStatus.IN_PROGRESS || session.currentQuestion == null) {
                return; // ronda ya cerrada
            }
            if (session.questionStartTimeMs != expectedStartMs) {
                return; // temporizador obsoleto de una ronda anterior
            }
            if (timeProvider.currentTimeMillis() - session.questionStartTimeMs < QUESTION_TIME_LIMIT_MS) {
                return; // aún no expiró (disparo anticipado, no debería ocurrir)
            }
            closeRoundAndAdvance(session);
        }
    }

    // ====================== Rondas y selección (FASE 4/6/7) ======================

    /**
     * Resultado de una ronda: la pregunta elegida al azar para los jugadores.
     */
    public record SelectedQuestion(int round, String category, Long questionId) {
    }

    /**
     * Selecciona la siguiente pregunta de la partida:
     * <ol>
     *   <li>consulta las preguntas activas NO usadas, agrupadas por categoría;</li>
     *   <li>descarta las categorías sin preguntas disponibles (se agotan y
     *       dejan de ser candidatas);</li>
     *   <li>elige UNA categoría al azar entre las disponibles;</li>
     *   <li>elige UNA pregunta al azar dentro de esa categoría;</li>
     *   <li>la registra como usada y avanza {@code currentRound}.</li>
     * </ol>
     *
     * <p>Nunca se repite una pregunta dentro de la misma partida. No hay un
     * orden fijo de categorías: cada ronda vuelve a sortearse desde cero.
     *
     * @param session la partida, en estado IN_PROGRESS y con rondas pendientes
     * @return la selección de la ronda o vacío si ya no hay preguntas
     *         disponibles; en ese caso se marca {@code session.insufficientQuestions}
     *         y el orquestador finaliza la partida antes de totalRounds
     * @throws IllegalArgumentException si la partida no está IN_PROGRESS o ya
     *                                  completó sus totalRounds rondas
     */
    public Optional<SelectedQuestion> selectNextQuestion(GameSession session) {
        synchronized (session) {
            if (session.status != GameStatus.IN_PROGRESS) {
                throw new IllegalArgumentException(
                        "La partida debe estar IN_PROGRESS para elegir preguntas");
            }
            if (session.currentRound >= session.totalRounds) {
                throw new IllegalArgumentException(
                        "La partida ya completó sus " + session.totalRounds + " rondas");
            }

            Map<String, List<Long>> pools = questionRepository
                    .findAvailableQuestionIdsGroupedByCategory(session.usedQuestionIds);
            pools.values().removeIf(List::isEmpty);

            if (pools.isEmpty()) {
                // No existen suficientes preguntas únicas para continuar.
                session.insufficientQuestions = true;
                return Optional.empty();
            }

            // 1) categoría al azar entre las disponibles
            List<String> categories = new ArrayList<>(pools.keySet());
            String category = categories.get(random.nextInt(categories.size()));

            // 2) pregunta al azar dentro de la categoría elegida
            List<Long> questionIds = pools.get(category);
            Long questionId = questionIds.get(random.nextInt(questionIds.size()));

            session.selectedCategory = category;
            session.currentQuestionId = questionId;
            session.usedQuestionIds.add(questionId);
            session.currentRound++;

            return Optional.of(new SelectedQuestion(session.currentRound, category, questionId));
        }
    }

    /**
     * Empieza la siguiente ronda: selecciona y carga la pregunta (nunca se
     * envía su correctOption), marca el instante de inicio y programa el
     * temporizador de 15 s. Asume el lock de session.
     */
    private void beginNextRound(GameSession session) {
        Optional<SelectedQuestion> next = selectNextQuestion(session);
        if (next.isEmpty()) {
            finishGame(session);
            return;
        }
        SelectedQuestion selected = next.get();
        Question question = questionRepository.findActiveQuestionById(selected.questionId())
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró la pregunta " + selected.questionId()));

        session.currentQuestion = question;
        session.currentRoundAnswers.clear();
        session.answerTimesMs.clear();
        session.players.forEach(p -> p.answeredCurrentQuestion = false);
        session.questionStartTimeMs = timeProvider.currentTimeMillis();

        Map<String, Object> payload = new HashMap<>();
        payload.put("round", selected.round());
        payload.put("category", selected.category());
        payload.put("questionId", selected.questionId());
        payload.put("question", question.question);
        payload.put("optionA", question.optionA);
        payload.put("optionB", question.optionB);
        payload.put("optionC", question.optionC);
        payload.put("optionD", question.optionD);
        payload.put("timeLimitMs", QUESTION_TIME_LIMIT_MS);
        if (session.mode == GameMode.LOCAL) {
            // En misma pantalla el sistema anuncia de quién es el turno.
            Player turn = currentTurnPlayer(session);
            if (turn != null) {
                payload.put("player", Map.of("playerId", turn.playerId, "nickname", turn.nickname));
            }
        }
        broadcast(session, GameEvents.NEW_QUESTION, payload);

        scheduleRoundTimer(session, session.questionStartTimeMs);
    }

    /**
     * Programa el cierre automático de la ronda actual.
     */
    private void scheduleRoundTimer(GameSession session, long startMs) {
        if (roundScheduler == null) {
            return;
        }
        cancelRoundTimer(session);
        session.roundTimerHandle = roundScheduler.schedule(
                () -> onRoundTimeout(session.gameId, startMs), QUESTION_TIME_LIMIT_MS);
    }

    /**
     * Cancela el temporizador de la ronda actual (si existe).
     */
    private void cancelRoundTimer(GameSession session) {
        if (roundScheduler != null && session.roundTimerHandle > 0) {
            roundScheduler.cancel(session.roundTimerHandle);
            session.roundTimerHandle = 0;
        }
    }

    /**
     * Cierra la ronda en curso: cancela el temporizador, calcula aciertos y
     * puntos (con bonus por rapidez), emite QUESTION_RESULT y decide entre la
     * siguiente ronda o el fin de la partida. Asume el lock de session.
     *
     * <p>En modo ONLINE se califica a todos los jugadores de la ronda; en
     * modo LOCAL solo al jugador del turno (los demás no jugaron la ronda y
     * no deben aparecer como "sin respuesta").
     */
    private void closeRoundAndAdvance(GameSession session) {
        cancelRoundTimer(session);

        Question question = session.currentQuestion;
        int round = session.currentRound;

        List<Player> toGrade;
        if (session.mode == GameMode.LOCAL) {
            Player turn = currentTurnPlayer(session);
            toGrade = turn == null ? List.of() : List.of(turn);
        } else {
            toGrade = session.players;
        }

        List<Map<String, Object>> answers = new ArrayList<>();
        for (Player player : toGrade) {
            String selected = session.currentRoundAnswers.get(player.playerId);
            boolean correct = selected != null && selected.equals(question.correctOption);
            int points = correct ? pointsFor(player, session) : 0;
            player.score += points;
            answers.add(Map.of(
                    "playerId", player.playerId,
                    "nickname", player.nickname,
                    "selectedOption", selected == null ? "" : selected,
                    "correct", correct,
                    "points", points,
                    "score", player.score));
        }

        broadcast(session, GameEvents.QUESTION_RESULT, Map.of(
                "round", round,
                "category", session.selectedCategory,
                "correctOption", question.correctOption,
                "answers", answers));

        session.currentQuestion = null;
        session.currentRoundAnswers.clear();
        session.answerTimesMs.clear();

        if (round >= session.totalRounds || session.insufficientQuestions) {
            finishGame(session);
        } else {
            broadcast(session, GameEvents.NEXT_QUESTION, Map.of("round", round + 1));
            beginNextRound(session);
        }
    }

    /**
     * Puntos de una respuesta correcta: 100 + bonus por rapidez según el
     * tiempo que quedaba al responder (el servidor mide el tiempo).
     */
    private int pointsFor(Player player, GameSession session) {
        Long answeredAtMs = session.answerTimesMs.get(player.playerId);
        long remainingMs = remainingTimeAt(answeredAtMs, session.questionStartTimeMs);
        return POINTS_CORRECT_ANSWER + speedBonus(remainingMs);
    }

    /** Tiempo restante al responder, acotado a [0, límite]. */
    private long remainingTimeAt(Long answeredAtMs, long startMs) {
        if (answeredAtMs == null) {
            return 0;
        }
        long remaining = QUESTION_TIME_LIMIT_MS - (answeredAtMs - startMs);
        return Math.max(0, Math.min(QUESTION_TIME_LIMIT_MS, remaining));
    }

    /**
     * Bonus por rapidez: redondeo de {@code MAX_SPEED_BONUS * remainingMs / límite}.
     * Responder al instante da 20; justo al límite da 0.
     */
    private int speedBonus(long remainingMs) {
        return (int) Math.round((double) MAX_SPEED_BONUS * remainingMs / QUESTION_TIME_LIMIT_MS);
    }

    /**
     * Finaliza la partida: ranking final, GAME_FINISHED y eliminación de la
     * partida de memoria. Asume el lock de session.
     */
    private void finishGame(GameSession session) {
        cancelRoundTimer(session);
        session.status = GameStatus.FINISHED;

        List<Map<String, Object>> ranking = session.players.stream()
                .sorted(Comparator.comparingInt((Player p) -> p.score).reversed()
                        .thenComparing(p -> p.nickname))
                .map(p -> Map.<String, Object>of(
                        "playerId", p.playerId,
                        "nickname", p.nickname,
                        "score", p.score))
                .toList();

        broadcast(session, GameEvents.GAME_FINISHED, Map.of(
                "ranking", ranking,
                "insufficientQuestions", session.insufficientQuestions,
                "message", session.insufficientQuestions
                        ? "No había suficientes preguntas disponibles para completar la partida"
                        : "¡Partida terminada!"));

        games.remove(session.gameId);
    }

    // ====================== Utilidades ======================

    private GameSession requireGame(String gameId) {
        GameSession session = games.get(normalizeGameId(gameId));
        if (session == null) {
            throw new GameNotFoundException(gameId);
        }
        return session;
    }

    private Player findPlayer(GameSession session, String playerId) {
        Player player = findPlayerOptional(session, playerId);
        if (player == null) {
            throw new IllegalArgumentException("El jugador no está en esta partida");
        }
        return player;
    }

    private Player findPlayerOptional(GameSession session, String playerId) {
        if (playerId == null) {
            return null;
        }
        return session.players.stream()
                .filter(p -> p.playerId.equals(playerId))
                .findFirst().orElse(null);
    }

    private long countConnected(GameSession session) {
        return session.players.stream().filter(p -> p.connected).count();
    }

    private long countAnswered(GameSession session) {
        return session.players.stream().filter(p -> p.answeredCurrentQuestion).count();
    }

    /**
     * Jugador al que le toca responder la ronda actual (solo modo LOCAL):
     * rotación en el orden de la sala — ronda 1 la responde el jugador[0],
     * ronda 2 el jugador[1], etc. En ONLINE devuelve {@code null}.
     */
    private Player currentTurnPlayer(GameSession session) {
        if (session.mode != GameMode.LOCAL || session.players.isEmpty()) {
            return null;
        }
        int index = (session.currentRound - 1) % session.players.size();
        return session.players.get(index);
    }

    private List<PlayerDto> playersPayload(GameSession session) {
        return session.players.stream()
                .map(p -> new PlayerDto(p.playerId, p.nickname, p.score, p.connected))
                .toList();
    }

    private void broadcast(GameSession session, String type, Object payload) {
        if (broadcaster != null) {
            broadcaster.broadcast(new WsMessage(type, session.gameId, payload));
        }
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("El apodo no puede estar vacío");
        }
        String clean = nickname.trim();
        if (clean.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException(
                    "El apodo no puede superar los " + MAX_NICKNAME_LENGTH + " caracteres");
        }
        return clean;
    }

    private String normalizeGameId(String gameId) {
        return gameId == null ? "" : gameId.trim().toUpperCase();
    }

    private String generateUniqueGameId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (true) {
            StringBuilder sb = new StringBuilder(GAME_ID_LENGTH);
            for (int i = 0; i < GAME_ID_LENGTH; i++) {
                sb.append(GAME_ID_ALPHABET.charAt(random.nextInt(GAME_ID_ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!games.containsKey(candidate)) {
                return candidate;
            }
        }
    }
}
