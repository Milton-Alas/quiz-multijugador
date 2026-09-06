package com.example.quiz.game.dto;

/**
 * Información pública de un jugador dentro de una partida.
 *
 * <p>Nunca incluye datos internos del servidor (p. ej. si ya respondió la
 * pregunta actual o la respuesta correcta).
 */
public record PlayerDto(String playerId, String nickname, int score, boolean connected) {
}
