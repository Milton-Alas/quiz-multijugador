package com.example.quiz.game.dto;

/**
 * Petición para crear una partida: cantidad de preguntas (5, 10 o 15) y
 * modo de juego opcional ("ONLINE" por defecto | "LOCAL").
 */
public record CreateGameRequest(Integer totalRounds, String mode) {
}
