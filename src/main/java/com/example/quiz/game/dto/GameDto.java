package com.example.quiz.game.dto;

import com.example.quiz.game.GameStatus;

import java.util.List;

/**
 * Vista pública de una partida.
 *
 * <p>Seguridad: este DTO es lo único que viaja al navegador sobre una
 * partida: NO contiene la pregunta actual, sus opciones ni la respuesta
 * correcta (eso llega por WebSocket solo durante la ronda).
 */
public record GameDto(String gameId,
                      GameStatus status,
                      int totalRounds,
                      int currentRound,
                      boolean insufficientQuestions,
                      List<PlayerDto> players) {
}
