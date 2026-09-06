package com.example.quiz.game;

/**
 * Partida no encontrada (404). Extiende IllegalArgumentException para no
 * romper los tests unitarios que esperaban esa excepción genérica; en REST
 * un ExceptionMapper más específico la convierte en HTTP 404.
 */
public class GameNotFoundException extends IllegalArgumentException {

    public GameNotFoundException(String gameId) {
        super("No existe la partida: " + gameId);
    }
}
