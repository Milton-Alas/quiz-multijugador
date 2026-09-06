package com.example.quiz.game;

import com.example.quiz.question.Question;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Estado completo de una partida activa, mantenido en memoria.
 *
 * <p>Los juegos NO se persisten en PostgreSQL (FASE 1-2 solo persisten
 * preguntas). Un {@code Map<String, GameSession>} en {@link GameService}
 * es el único dueño del estado; cuando la partida finaliza se elimina.
 *
 * <p>FASE 3: identidad, configuración y jugadores. FASE 4: selección
 * aleatoria. FASE 6: pregunta y respuestas de la ronda. FASE 7: control del
 * tiempo por pregunta (inicio, temporizador y tiempos de respuesta).
 */
public class GameSession {

    /** Identificador corto y legible de la partida (lo teclean los jugadores). */
    public final String gameId;

    /** Cantidad de preguntas de la partida: 5, 10 o 15. */
    public final int totalRounds;

    /** Modalidad de juego: ONLINE (cada quien en su dispositivo) o LOCAL (misma pantalla). */
    public final GameMode mode;

    /** Jugadores en orden de llegada. */
    public final List<Player> players = new ArrayList<>();

    /** Estado actual de la partida. */
    public GameStatus status = GameStatus.LOBBY;

    /** Ronda en curso (0 = aún no ha empezado; 1..totalRounds en juego). */
    public int currentRound = 0;

    /** Ids de preguntas ya usadas en esta partida: nunca pueden repetirse. */
    public final Set<Long> usedQuestionIds = new HashSet<>();

    /** Categoría elegida al azar para la ronda en curso. */
    public String selectedCategory;

    /** Id de la pregunta de la ronda en curso. */
    public Long currentQuestionId;

    /**
     * Pregunta de la ronda en curso (cargada por el GameService). Vive solo
     * en el servidor: su {@code correctOption} jamás viaja en un evento.
     */
    public Question currentQuestion;

    /** Respuestas de la ronda en curso: playerId -> opción elegida ("A".."D"). */
    public final Map<String, String> currentRoundAnswers = new HashMap<>();

    /** Instante (reloj del servidor) en que se emitió la pregunta actual. */
    public long questionStartTimeMs;

    /** Identificador del temporizador de 15 s de la ronda actual (0 = ninguno). */
    public long roundTimerHandle;

    /** Instante (reloj del servidor) en que respondió cada jugador. */
    public final Map<String, Long> answerTimesMs = new HashMap<>();

    /** true si la partida debe terminar antes de totalRounds porque no quedan
     *  preguntas únicas disponibles (se informa a los jugadores). */
    public boolean insufficientQuestions;

    public GameSession(String gameId, int totalRounds) {
        this(gameId, totalRounds, GameMode.ONLINE);
    }

    public GameSession(String gameId, int totalRounds, GameMode mode) {
        this.gameId = gameId;
        this.totalRounds = totalRounds;
        this.mode = mode == null ? GameMode.ONLINE : mode;
    }
}
