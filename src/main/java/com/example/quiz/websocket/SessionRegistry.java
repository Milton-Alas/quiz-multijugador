package com.example.quiz.websocket;

import com.example.quiz.game.GameEventBroadcaster;
import com.example.quiz.game.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Registro en memoria de las conexiones WebSocket, agrupadas por partida.
 *
 * <p>Es la implementación de {@link GameEventBroadcaster}: el GameService le
 * entrega eventos y esta clase los serializa y envía a todas las sesiones de
 * la partida. También guarda qué jugador corresponde a cada sesión para que
 * el {@link GameWebSocket} sepa quién envía cada mensaje.
 */
@ApplicationScoped
public class SessionRegistry implements GameEventBroadcaster {

    /** Sesión WebSocket -> partida y jugador al que pertenece. */
    public record Binding(String gameId, String playerId) {
    }

    private final Map<String, Binding> bindingsBySessionId = new ConcurrentHashMap<>();
    private final Map<String, Set<Session>> sessionsByGame = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    @Inject
    public SessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Asocia la sesión a una partida/jugador y la añade al canal de la partida. */
    public void bind(Session session, String gameId, String playerId) {
        Binding previous = bindingsBySessionId.put(session.getId(), new Binding(gameId, playerId));
        if (previous != null && !previous.gameId().equals(gameId)) {
            removeFromGame(previous.gameId(), session);
        }
        sessionsByGame.computeIfAbsent(gameId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    /** Quita la sesión del registro y devuelve su vínculo anterior (si lo había). */
    public Binding unbind(Session session) {
        Binding binding = bindingsBySessionId.remove(session.getId());
        if (binding != null) {
            removeFromGame(binding.gameId(), session);
        }
        return binding;
    }

    /** Vínculo (partida/jugador) de una sesión, o null si aún no hizo JOIN. */
    public Binding bindingOf(Session session) {
        return bindingsBySessionId.get(session.getId());
    }

    @Override
    public void broadcast(WsMessage message) {
        Set<Session> sessions = sessionsByGame.getOrDefault(message.gameId(), Set.of());
        for (Session session : sessions) {
            send(session, message);
        }
    }

    /** Envía un mensaje a una única sesión. */
    public void send(Session session, WsMessage message) {
        try {
            synchronized (session) {
                session.getBasicRemote().sendText(objectMapper.writeValueAsString(message));
            }
        } catch (Exception e) {
            // Sesión rota o cerrada: se limpia ahora y en onClose
            unbind(session);
        }
    }

    private void removeFromGame(String gameId, Session session) {
        Set<Session> sessions = sessionsByGame.get(gameId);
        if (sessions != null) {
            sessions.remove(session);
        }
    }
}
