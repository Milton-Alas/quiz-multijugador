package com.example.quiz.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado completo de una partida activa, mantenido en memoria.
 *
 * <p>Los juegos NO se persisten en PostgreSQL (FASE 1-2 solo persisten
 * preguntas). Un {@code Map<String, GameSession>} en {@link GameService}
 * es el único dueño del estado; cuando la partida finaliza se elimina.
 *
 * <p>Campos añadidos en fases posteriores (no declarados aún para no dejar
 * código muerto): currentQuestion, usedQuestionIds, selectedCategory,
 * questionStartTime y respuestas por ronda.
 */
public class GameSession {

    /** Identificador corto y legible de la partida (lo teclean los jugadores). */
    public final String gameId;

    /** Cantidad de preguntas de la partida: 5, 10 o 15. */
    public final int totalRounds;

    /** Jugadores en orden de llegada. */
    public final List<Player> players = new ArrayList<>();

    /** Estado actual de la partida. */
    public GameStatus status = GameStatus.LOBBY;

    /** Ronda en curso (0 = aún no ha empezado; 1..totalRounds en juego). */
    public int currentRound = 0;

    public GameSession(String gameId, int totalRounds) {
        this.gameId = gameId;
        this.totalRounds = totalRounds;
    }
}
