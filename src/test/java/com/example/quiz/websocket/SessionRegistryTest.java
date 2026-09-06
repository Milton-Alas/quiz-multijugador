package com.example.quiz.websocket;

import com.example.quiz.game.WsMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.SendHandler;
import jakarta.websocket.SendResult;
import jakarta.websocket.Session;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El envío de eventos WebSocket debe ser SIEMPRE asíncrono.
 *
 * <p>Regresión: un broadcast síncrono ({@code getBasicRemote().sendText})
 * desde un event loop de Vert.x bloqueaba el servidor entero cuando un
 * cliente dejaba de leer (pestaña dormida o red cortada sin cierre TCP):
 * el envío esperaba para siempre y arrastraba el monitor de la partida y
 * los event loops. Estas pruebas garantizan que SessionRegistry usa la API
 * asíncrona, ignora/limpia sesiones cerradas y sobrevive a envíos fallidos.
 */
class SessionRegistryTest {

    /** Sesión WebSocket falsa: implementa Session por Proxy, solo lo que usa SessionRegistry. */
    private static final class FakeSession {
        final String id;
        final Session session;
        boolean open = true;
        boolean failNextSend;
        final List<String> sent = new ArrayList<>();
        int closeCalls;

        FakeSession(String id) {
            this.id = id;
            this.session = (Session) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Session.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> "FakeSession(" + id + ")";
                        case "getId" -> id;
                        case "isOpen" -> open;
                        case "close" -> {
                            closeCalls++;
                            open = false;
                            yield null;
                        }
                        case "getAsyncRemote" -> asyncRemoteProxy();
                        default -> throw new UnsupportedOperationException(
                                "Método no usado por SessionRegistry en el test: " + method);
                    });
        }

        private RemoteEndpoint.Async asyncRemoteProxy() {
            return (RemoteEndpoint.Async) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{RemoteEndpoint.Async.class},
                    (proxy, method, args) -> {
                        if (!method.getName().equals("sendText") || args.length != 2) {
                            throw new UnsupportedOperationException(
                                    "Método no usado por SessionRegistry en el test: " + method);
                        }
                        String payload = (String) args[0];
                        SendHandler callback = (SendHandler) args[1];
                        if (failNextSend) {
                            failNextSend = false;
                            callback.onResult(new SendResult(new IllegalStateException("cliente roto")));
                        } else {
                            sent.add(payload);
                            callback.onResult(new SendResult());
                        }
                        return null;
                    });
        }
    }

    private final SessionRegistry registry = new SessionRegistry(new ObjectMapper());

    private WsMessage someMessage(String gameId) {
        return new WsMessage("PLAYER_JOINED", gameId, Map.of("nickname", "Ana"));
    }

    @Test
    void broadcastDeliversJsonToEveryBoundOpenSession() {
        FakeSession ana = new FakeSession("s1");
        FakeSession luis = new FakeSession("s2");
        registry.bind(ana.session, "GAME1", "p1");
        registry.bind(luis.session, "GAME1", "p2");

        registry.broadcast(someMessage("GAME1"));

        assertEquals(1, ana.sent.size(), "Ana debe recibir el evento");
        assertEquals(1, luis.sent.size(), "Luis debe recibir el evento");
        assertEquals(ana.sent.get(0), luis.sent.get(0), "Ambos reciben el mismo mensaje");
        assertTrue(ana.sent.get(0).contains("\"type\":\"PLAYER_JOINED\""));
    }

    @Test
    void closedSessionIsRemovedWithoutReceiving() {
        FakeSession stale = new FakeSession("s1");
        registry.bind(stale.session, "GAME1", "p1");
        stale.open = false;

        registry.broadcast(someMessage("GAME1")); // no debe lanzar ni colgarse

        assertEquals(0, stale.sent.size(), "Una sesión cerrada no recibe nada");
        assertNull(registry.bindingOf(stale.session), "La sesión cerrada se desvincula");
    }

    @Test
    void failedAsyncSendClosesAndUnbindsTheBrokenSession() {
        FakeSession broken = new FakeSession("s1");
        registry.bind(broken.session, "GAME1", "p1");
        broken.failNextSend = true;

        registry.broadcast(someMessage("GAME1")); // el envío falla: no debe lanzar

        assertTrue(broken.closeCalls >= 1, "La sesión rota debe cerrarse");
        assertFalse(broken.open);
        assertNull(registry.bindingOf(broken.session), "La sesión rota se desvincula");
        assertEquals(0, broken.sent.size());
    }

    @Test
    void broadcastDoesNotAffectOtherGames() {
        FakeSession a = new FakeSession("s1");
        FakeSession b = new FakeSession("s2");
        registry.bind(a.session, "GAME1", "p1");
        registry.bind(b.session, "GAME2", "p1");

        registry.broadcast(someMessage("GAME1"));

        assertEquals(1, a.sent.size());
        assertEquals(0, b.sent.size(), "El evento de GAME1 no llega a GAME2");
    }
}
