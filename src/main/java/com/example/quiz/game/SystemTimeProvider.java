package com.example.quiz.game;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Reloj real del sistema (System.currentTimeMillis).
 */
@ApplicationScoped
public class SystemTimeProvider implements TimeProvider {

    @Override
    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
