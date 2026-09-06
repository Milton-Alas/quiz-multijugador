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
            if (!session.isOpen()) {
                // Sesión muerta detectada antes de enviar: se limpia y se sigue.
                unbind(session);
                continue;
            }
            send(session, message);
        }
    }

    /**
     * Envía un mensaje a una única sesión con la API ASÍNCRONA.
     *
     * <p>Regla de oro: el envío nunca debe bloquear el hilo que llama. Estos
     * broadcasts corren en los event loops de Vert.x (y, vía GameService, bajo
     * el monitor de la partida); {@code getBasicRemote().sendText} es una
     * escritura síncrona que, si el cliente deja de leer (pestaña dormida, red
     * cortada sin cierre TCP), esperaba para siempre al promesa del canal:
     * eso congelaba el event loop y, con él, todas las partidas y peticiones.
     * Con {@code getAsyncRemote().sendText} la escritura se encola y el
     * resultado llega por callback; un fallo limpia la sesión rota.
     */
    public void send(Session session, WsMessage message) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            unbind(session); // ni siquiera se puede serializar: sesión inviable
            return;
        }
        synchronized (session) {
            if (!session.isOpen()) {
                unbind(session);
                return;
            }
            try {
                session.getAsyncRemote().sendText(payload, result -> {
                    if (!result.isOK()) {
                        cleanupBrokenSession(session);
                    }
                });
            } catch (Exception e) {
                // Lanzado al encolar (sesión cerrada a mitad de envío, etc.)
                cleanupBrokenSession(session);
            }
        }
    }

    /**
     * Cierra (si sigue abierta) y desvincula una sesión cuyo envío falló.
     * Al cerrar, {@code onClose} del endpoint avisa al GameService
     * (jugador desconectado); el unbind de aquí es idempotente.
     */
    private void cleanupBrokenSession(Session session) {
        try {
            if (session.isOpen()) {
                session.close();
            }
        } catch (Exception e) {
            // Sesión rota o ya cerrada: la limpieza la completa onClose
        } finally {
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
