package com.example.quiz.game;

/**
 * Jugador de una partida activa.
 *
 * <p>Vive únicamente en memoria dentro de una {@link GameSession}: no hay
 * registro/login ni persistencia de jugadores en esta versión (MVP).
 */
public class Player {

    /** Identificador único interno del jugador (generado por el servidor). */
    public final String playerId;

    /** Apodo visible para los demás jugadores. */
    public final String nickname;

    /** Puntos acumulados en la partida actual. */
    public int score;

    /** Indica si el WebSocket del jugador sigue conectado. */
    public boolean connected;

    /** Indica si ya respondió la pregunta de la ronda actual. */
    public boolean answeredCurrentQuestion;

    public Player(String playerId, String nickname) {
        this.playerId = playerId;
        this.nickname = nickname;
        this.score = 0;
        this.connected = true;
        this.answeredCurrentQuestion = false;
    }
}
