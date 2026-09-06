package com.example.quiz.game;

import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Temporizador real basado en Vert.x (bucle de eventos de Quarkus).
 */
@ApplicationScoped
public class VertxRoundScheduler implements RoundScheduler {

    private final Vertx vertx;

    @Inject
    public VertxRoundScheduler(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public long schedule(Runnable task, long delayMs) {
        return vertx.setTimer(delayMs, id -> vertx.executeBlocking(() -> {
            // El cierre de ronda consulta PostgreSQL con JTA, prohibido en el
            // IO thread: el temporizador dispara en el event loop, así que la
            // tarea se ejecuta en un hilo worker del pool de Vert.x.
            task.run();
            return null;
        }));
    }

    @Override
    public void cancel(long handle) {
        if (handle > 0) {
            vertx.cancelTimer(handle);
        }
    }
}
