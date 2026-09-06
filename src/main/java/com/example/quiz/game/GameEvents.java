package com.example.quiz.game;

/**
 * Eventos del protocolo WebSocket (server -> client y estados).
 *
 * <p>La creación de la partida ocurre por REST (POST /api/games); su
 * respuesta 201 ya informa al creador, por lo que GAME_CREATED se mantiene
 * como constante reservada por si más adelante conviene emitirlo también.
 */
public final class GameEvents {

    private GameEvents() {
    }

    /** La partida se creó (reservado: hoy la crea REST). */
    public static final String GAME_CREATED = "GAME_CREATED";

    /** Un jugador (re)conectó su WebSocket a la partida. */
    public static final String PLAYER_JOINED = "PLAYER_JOINED";

    /** Un jugador desconectó. */
    public static final String PLAYER_LEFT = "PLAYER_LEFT";

    /** La partida empezó (sala de espera -> juego). */
    public static final String GAME_STARTED = "GAME_STARTED";

    /** Nueva pregunta para la ronda (sin la respuesta correcta). */
    public static final String NEW_QUESTION = "NEW_QUESTION";

    /** Un jugador envió su respuesta (sin revelar si es correcta). */
    public static final String PLAYER_ANSWERED = "PLAYER_ANSWERED";

    /** Resultado de la ronda (revela la opción correcta y los puntos). */
    public static final String QUESTION_RESULT = "QUESTION_RESULT";

    /** Aviso de transición hacia la siguiente ronda. */
    public static final String NEXT_QUESTION = "NEXT_QUESTION";

    /** La partida terminó: ranking final. */
    public static final String GAME_FINISHED = "GAME_FINISHED";

    /** Error puntual devuelto a un solo cliente. */
    public static final String ERROR = "ERROR";
}
