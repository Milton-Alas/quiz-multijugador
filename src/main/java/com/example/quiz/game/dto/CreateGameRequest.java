package com.example.quiz.game.dto;

/**
 * Petición para crear una partida: cantidad de preguntas (5, 10 o 15).
 */
public record CreateGameRequest(Integer totalRounds) {
}
