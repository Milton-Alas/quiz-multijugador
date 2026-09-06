package com.example.quiz.game;

/**
 * Programa el cierre automático de una ronda a los 15 s. El GameService
 * cancela el temporizador cuando la ronda se cierra antes (todos respondieron).
 * En tests se usa un doble que permite disparar la tarea manualmente.
 */
public interface RoundScheduler {

    /**
     * Programa la tarea dentro de {@code delayMs}.
     *
     * @return identificador del temporizador (para cancelarlo)
     */
    long schedule(Runnable task, long delayMs);

    /** Cancela un temporizador pendiente (si ya disparó, no hace nada). */
    void cancel(long handle);
}
