package com.example.quiz.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitarios de la FASE 3 (sin base de datos, sin Quarkus):
 * creación de partidas y unión de jugadores en memoria.
 */
class GameServiceTest {

    private GameService gameService;

    @BeforeEach
    void setUp() {
        gameService = new GameService();
    }

    // ---------- Creación de partidas ----------

    @Test
    void createGameWithFiveQuestions() {
        GameSession session = gameService.createGame(5);

        assertNotNull(session.gameId);
        assertFalse(session.gameId.isBlank());
        assertEquals(5, session.totalRounds);
        assertEquals(GameStatus.LOBBY, session.status);
        assertEquals(0, session.currentRound);
        assertTrue(session.players.isEmpty());
    }

    @Test
    void createGameAcceptsOnlyFiveTenFifteenRounds() {
        for (int allowed : GameService.ALLOWED_TOTAL_ROUNDS) {
            GameSession session = gameService.createGame(allowed);
            assertEquals(allowed, session.totalRounds);
        }

        for (int invalid : new int[]{0, 1, 4, 7, 8, 20, 100, -5}) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> gameService.createGame(invalid), "totalRounds=" + invalid + " debe rechazarse");
            assertTrue(ex.getMessage().contains("inválida"), ex.getMessage());
        }
    }

    @Test
    void gameIdsAreUniqueAcrossCreatedGames() {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            ids.add(gameService.createGame(5).gameId);
        }
        assertEquals(200, ids.size(), "los gameIds generados deben ser únicos");
    }

    // ---------- Unión de jugadores ----------

    @Test
    void playersJoinWithInitialStateAndJoinOrder() {
        GameSession session = gameService.createGame(5);

        Player ana = gameService.joinGame(session.gameId, "Ana");
        Player beto = gameService.joinGame(session.gameId, "Beto");
        Player carla = gameService.joinGame(session.gameId, "Carla");

        assertEquals(3, session.players.size());
        assertEquals(java.util.List.of("Ana", "Beto", "Carla"),
                session.players.stream().map(p -> p.nickname).toList());

        for (Player p : java.util.List.of(ana, beto, carla)) {
            assertNotNull(p.playerId);
            assertEquals(0, p.score);
            assertTrue(p.connected);
            assertFalse(p.answeredCurrentQuestion);
        }

        // Cada jugador tiene un playerId distinto
        assertNotEquals(ana.playerId, beto.playerId);
        assertNotEquals(beto.playerId, carla.playerId);
    }

    @Test
    void joinTrimsNicknameAndIsCaseInsensitiveForDuplicates() {
        GameSession session = gameService.createGame(5);
        gameService.joinGame(session.gameId, "  Ana  ");

        // "ana" ya está en uso (duplicado sin importar mayúsculas/espacios)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame(session.gameId, "ana"));
        assertTrue(ex.getMessage().contains("ya está en uso"), ex.getMessage());

        // El apodo guardado está recortado
        assertEquals("Ana", session.players.get(0).nickname);
    }

    @Test
    void joinRejectsBlankOrTooLongNicknames() {
        GameSession session = gameService.createGame(5);

        assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame(session.gameId, null));
        assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame(session.gameId, "   "));
        assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame(session.gameId, "a".repeat(GameService.MAX_NICKNAME_LENGTH + 1)));
    }

    @Test
    void joinRejectsUnknownGameAndNonLobbyGame() {
        assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame("XXXXX", "Ana"));
        assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame(null, "Ana"));

        GameSession session = gameService.createGame(5);
        gameService.joinGame(session.gameId, "Ana");
        session.status = GameStatus.IN_PROGRESS;

        assertThrows(IllegalArgumentException.class,
                () -> gameService.joinGame(session.gameId, "Beto"));
    }

    @Test
    void gameIdLookupIsCaseInsensitive() {
        GameSession session = gameService.createGame(5);

        // El jugador teclea el código en minúsculas para unirse
        gameService.joinGame(session.gameId.toLowerCase(), "Ana");

        assertEquals(session, gameService.findGame(session.gameId).orElseThrow());
        assertEquals(session, gameService.findGame(session.gameId.toLowerCase()).orElseThrow());
        assertTrue(gameService.findGame(session.gameId).isPresent());
        assertFalse(gameService.findGame("NOEXISTE").isPresent());
    }

    @Test
    void gamesAreIndependent() {
        GameSession gameA = gameService.createGame(5);
        GameSession gameB = gameService.createGame(10);

        gameService.joinGame(gameA.gameId, "Ana");
        gameService.joinGame(gameA.gameId, "Beto");
        gameService.joinGame(gameB.gameId, "Ana"); // mismo apodo, distinta partida: permitido

        assertEquals(2, gameA.players.size());
        assertEquals(1, gameB.players.size());
        assertNotEquals(gameA.gameId, gameB.gameId);
        assertEquals(10, gameB.totalRounds);
    }
}
