package com.example.quiz.game;

/**
 * Reloj del servidor en milisegundos. El servidor es la autoridad del tiempo
 * de cada pregunta; en tests se inyecta un reloj manual para no esperar 15 s.
 */
public interface TimeProvider {

    long currentTimeMillis();
}
