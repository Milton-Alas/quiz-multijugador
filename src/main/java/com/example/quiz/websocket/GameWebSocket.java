package com.example.quiz.websocket;

import com.example.quiz.game.GameEvents;
import com.example.quiz.game.GameService;
import com.example.quiz.game.WsMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Endpoint WebSocket del juego: {@code /ws/game}.
 *
 * <p>Mensajes entrantes (JSON plano): JOIN (gameId, playerId), START_GAME y
 * ANSWER (option; en modo LOCAL además playerId del jugador que responde su
 * turno). El jugador debe hacer JOIN antes de poder iniciar o responder. Los
 * eventos de salida usan el envoltorio estándar
 * {"type", "gameId", "payload"} y los emite el GameService vía broadcaster.
 *
 * <p>Hilos: los handlers de Undertow corren en el event loop de Vert.x, pero
 * el motor de juego consulta PostgreSQL con transacciones JTA, que Quarkus
 * prohíbe en el IO thread ("Cannot start a JTA transaction from the IO
 * thread"). Por eso cada mensaje se despacha a un hilo worker del pool de
 * Vert.x antes de tocar el GameService. La concurrencia por partida la
 * garantiza el propio {@code synchronized(session)} del GameService.
 *
 * <p>Regla de seguridad: el servidor es la autoridad (tiempo, respuestas y
 * puntuaciones). El navegador solo envía intenciones.
 */
@ServerEndpoint("/ws/game")
public class GameWebSocket {

    private static final Logger LOG = Logger.getLogger(GameWebSocket.class);

    private final SessionRegistry registry;
    private final GameService gameService;
    private final ObjectMapper objectMapper;
    private final Vertx vertx;

    @Inject
    public GameWebSocket(SessionRegistry registry, GameService gameService,
                         ObjectMapper objectMapper, Vertx vertx) {
        this.registry = registry;
        this.gameService = gameService;
        this.objectMapper = objectMapper;
        this.vertx = vertx;
    }

    @OnOpen
    public void onOpen(Session session) {
        // La sesión queda pendiente hasta recibir JOIN
    }

    @OnMessage
    public void onMessage(String text, Session session) {
        // Fuera del IO thread: START_GAME/ANSWER llegan a PostgreSQL (JTA).
        vertx.executeBlocking(() -> {
            dispatch(text, session);
            return null;
        }).onFailure(error -> LOG.errorf(error, "Error procesando mensaje WebSocket: %s", text));
    }

    private void dispatch(String text, Session session) {
        try {
            JsonNode message = objectMapper.readTree(text);
            String type = message.path("type").asText("");
            switch (type) {
                case "JOIN" -> handleJoin(session, message);
                case "START_GAME" -> handleStartGame(session);
                case "ANSWER" -> handleAnswer(session, message);
                default -> sendError(session, null, "Tipo de mensaje desconocido: '" + type + "'");
            }
        } catch (Exception e) {
            sendError(session, registry.bindingOf(session) == null ? null
                    : registry.bindingOf(session).gameId(), e.getMessage());
        }
    }

    private void handleJoin(Session session, JsonNode message) {
        String gameId = message.path("gameId").asText("");
        String playerId = message.path("playerId").asText("");
        if (gameId.isBlank() || playerId.isBlank()) {
            throw new IllegalArgumentException("Faltan gameId o playerId en el JOIN");
        }
        String canonicalGameId = gameService.validatePlayerInGame(gameId, playerId);
        registry.bind(session, canonicalGameId, playerId);
        gameService.onPlayerReconnected(canonicalGameId, playerId);
    }

    private void handleStartGame(Session session) {
        SessionRegistry.Binding binding = requireBinding(session);
        gameService.startGame(binding.gameId(), binding.playerId());
    }

    private void handleAnswer(Session session, JsonNode message) {
        SessionRegistry.Binding binding = requireBinding(session);
        String option = message.path("option").asText(null);
        // En modo LOCAL (misma pantalla) el mensaje indica qué jugador
        // responde; en ONLINE siempre responde el enlazado a la sesión.
        String declared = message.path("playerId").asText("");
        String playerId = (declared == null || declared.isBlank()) ? binding.playerId() : declared;
        gameService.submitAnswer(binding.gameId(), binding.playerId(), playerId, option);
    }

    @OnClose
    public void onClose(Session session) {
        SessionRegistry.Binding binding = registry.unbind(session);
        if (binding != null) {
            gameService.playerLeft(binding.gameId(), binding.playerId());
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        // La sesión se limpia al cerrarse (onClose)
    }

    private SessionRegistry.Binding requireBinding(Session session) {
        SessionRegistry.Binding binding = registry.bindingOf(session);
        if (binding == null) {
            throw new IllegalStateException("Primero debes enviar un mensaje JOIN");
        }
        return binding;
    }

    private void sendError(Session session, String gameId, String message) {
        registry.send(session, new WsMessage(GameEvents.ERROR, gameId,
                Map.of("message", message == null ? "Error interno" : message)));
    }
}
