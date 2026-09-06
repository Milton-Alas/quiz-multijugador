package com.example.quiz.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Temporizador de prueba: no dispara solo; el test invoca {@link #fire}.
 * {@link #cancel} impide que una tarea cancelada pueda dispararse.
 */
class FakeRoundScheduler implements RoundScheduler {

    private final Map<Long, Runnable> tasks = new HashMap<>();
    private long nextId = 1;

    @Override
    public long schedule(Runnable task, long delayMs) {
        tasks.put(nextId, task);
        return nextId++;
    }

    @Override
    public void cancel(long handle) {
        tasks.remove(handle);
    }

    /** Identificador del último temporizador programado. */
    long lastHandle() {
        return nextId - 1;
    }

    /** Dispara la tarea si sigue programada. Devuelve false si estaba cancelada. */
    boolean fire(long handle) {
        Runnable task = tasks.remove(handle);
        if (task != null) {
            task.run();
            return true;
        }
        return false;
    }
}
