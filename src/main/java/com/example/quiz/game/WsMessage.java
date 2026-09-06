package com.example.quiz.game;

/**
 * Envoltorio estándar de un evento WebSocket:
 * {"type": "...", "gameId": "...", "payload": {...}}
 */
public record WsMessage(String type, String gameId, Object payload) {
}
