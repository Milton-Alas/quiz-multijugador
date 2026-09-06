package com.example.quiz.game;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ciclo de vida de las partidas activas. Singleton en memoria: un
 * {@code Map<String, GameSession>} con el estado de todos los juegos.
 *
 * <p>FASE 3 cubre crear una partida y unirse a ella. Las rondas, el tiempo
 * y las respuestas se incorporan en fases posteriores. Toda la mutación de
 * una misma partida se serializa con {@code synchronized(session)}.
 *
 * <p>Diseño: los juegos no se persisten; en una versión futura este estado
 * podría migrar a Redis si se necesitan varias instancias del backend.
 */
@ApplicationScoped
public class GameService {

    /** Cantidades de preguntas permitidas por partida. */
    public static final List<Integer> ALLOWED_TOTAL_ROUNDS = List.of(5, 10, 15);

    /** Longitud máxima del apodo. */
    public static final int MAX_NICKNAME_LENGTH = 20;

    /** Alfabeto del gameId sin caracteres ambiguos (0, O, 1, I). */
    private static final String GAME_ID_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int GAME_ID_LENGTH = 5;

    private final Map<String, GameSession> games = new ConcurrentHashMap<>();

    /**
     * Crea una partida nueva en estado LOBBY.
     *
     * @param totalRounds cantidad de preguntas: debe ser 5, 10 o 15
     * @return la sesión creada
     * @throws IllegalArgumentException si totalRounds no está permitido
     */
    public GameSession createGame(int totalRounds) {
        if (!ALLOWED_TOTAL_ROUNDS.contains(totalRounds)) {
            throw new IllegalArgumentException(
                    "Cantidad de preguntas inválida (" + totalRounds
                            + "). Permitidas: " + ALLOWED_TOTAL_ROUNDS);
        }
        GameSession session = new GameSession(generateUniqueGameId(), totalRounds);
        games.put(session.gameId, session);
        return session;
    }

    /**
     * Añade un jugador a la partida (estado LOBBY).
     *
     * @param gameId   identificador de la partida (no distingue mayúsculas)
     * @param nickname apodo visible, de 1 a {@value #MAX_NICKNAME_LENGTH} caracteres
     * @return el jugador creado, con score 0 y conectado
     * @throws IllegalArgumentException si la partida no existe, ya no admite
     *                                  jugadores o el apodo no es válido/está repetido
     */
    public Player joinGame(String gameId, String nickname) {
        GameSession session = games.get(normalizeGameId(gameId));
        if (session == null) {
            throw new IllegalArgumentException("No existe la partida: " + gameId);
        }
        synchronized (session) {
            if (session.status != GameStatus.LOBBY) {
                throw new IllegalArgumentException("La partida ya no acepta jugadores");
            }
            String cleanNickname = normalizeNickname(nickname);
            boolean nicknameTaken = session.players.stream()
                    .anyMatch(p -> p.nickname.equalsIgnoreCase(cleanNickname));
            if (nicknameTaken) {
                throw new IllegalArgumentException(
                        "El apodo '" + cleanNickname + "' ya está en uso en esta partida");
            }
            Player player = new Player(UUID.randomUUID().toString(), cleanNickname);
            session.players.add(player);
            return player;
        }
    }

    /**
     * Devuelve la sesión de una partida, si existe (solo lectura).
     */
    public Optional<GameSession> findGame(String gameId) {
        if (gameId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(games.get(normalizeGameId(gameId)));
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("El apodo no puede estar vacío");
        }
        String clean = nickname.trim();
        if (clean.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException(
                    "El apodo no puede superar los " + MAX_NICKNAME_LENGTH + " caracteres");
        }
        return clean;
    }

    private String normalizeGameId(String gameId) {
        return gameId == null ? "" : gameId.trim().toUpperCase();
    }

    private String generateUniqueGameId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        while (true) {
            StringBuilder sb = new StringBuilder(GAME_ID_LENGTH);
            for (int i = 0; i < GAME_ID_LENGTH; i++) {
                sb.append(GAME_ID_ALPHABET.charAt(random.nextInt(GAME_ID_ALPHABET.length())));
            }
            String candidate = sb.toString();
            if (!games.containsKey(candidate)) {
                return candidate;
            }
        }
    }
}
