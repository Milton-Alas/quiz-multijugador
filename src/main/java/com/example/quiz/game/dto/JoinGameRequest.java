package com.example.quiz.game.dto;

/**
 * Petición para unirse a una partida: apodo visible del jugador.
 */
public record JoinGameRequest(String nickname) {
}
