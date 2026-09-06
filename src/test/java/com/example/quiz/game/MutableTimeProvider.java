package com.example.quiz.game;

/**
 * Reloj de prueba: el test controla el valor devuelto (no espera 15 s reales).
 */
class MutableTimeProvider implements TimeProvider {

    private long nowMs;

    MutableTimeProvider(long startMs) {
        this.nowMs = startMs;
    }

    @Override
    public long currentTimeMillis() {
        return nowMs;
    }

    void advance(long deltaMs) {
        nowMs += deltaMs;
    }
}
