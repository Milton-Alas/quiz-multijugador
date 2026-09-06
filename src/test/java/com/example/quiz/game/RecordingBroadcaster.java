package com.example.quiz.game;

import java.util.ArrayList;
import java.util.List;

/**
 * Broadcaster de prueba: registra cada evento emitido por el GameService.
 */
class RecordingBroadcaster implements GameEventBroadcaster {

    private final List<WsMessage> messages = new ArrayList<>();

    @Override
    public synchronized void broadcast(WsMessage message) {
        messages.add(message);
    }

    synchronized List<WsMessage> ofType(String type) {
        return messages.stream().filter(m -> m.type().equals(type)).toList();
    }

    synchronized WsMessage lastOfType(String type) {
        List<WsMessage> matches = ofType(type);
        if (matches.isEmpty()) {
            throw new AssertionError("Debe existir un evento " + type);
        }
        return matches.get(matches.size() - 1);
    }

    synchronized int countOfType(String type) {
        return ofType(type).size();
    }
}
