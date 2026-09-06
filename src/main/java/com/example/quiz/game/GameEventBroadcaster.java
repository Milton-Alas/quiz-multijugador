package com.example.quiz.game;

/**
 * Canal por el que el {@link GameService} emite los eventos de una partida
 * hacia sus jugadores. Lo implementa la capa de WebSockets (SessionRegistry);
 * en tests unitarios se usa un doble que registra los mensajes.
 */
public interface GameEventBroadcaster {

    /** Envía el mensaje a todas las sesiones conectadas de la partida. */
    void broadcast(WsMessage message);
}
