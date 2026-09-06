package com.example.quiz.game;

/**
 * Estados por los que pasa una partida.
 */
public enum GameStatus {
    /** Sala de espera: se aceptan jugadores. */
    LOBBY,
    /** Partida en curso (rondas activas). */
    IN_PROGRESS,
    /** Partida terminada; se muestra el ranking y luego se descarta de memoria. */
    FINISHED
}
