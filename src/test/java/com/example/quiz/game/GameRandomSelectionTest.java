package com.example.quiz.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la FASE 4: reglas de selección aleatoria de categorías y
 * preguntas, sin repetición dentro de una partida.
 */
class GameRandomSelectionTest {

    private FakeQuestionRepository fakeRepo;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeQuestionRepository();
    }

    private GameService serviceWithSeed(long seed) {
        return new GameService(fakeRepo, new Random(seed));
    }

    private GameService serviceUnseeded() {
        return new GameService(fakeRepo, new Random());
    }

    private GameSession newInProgressGame(GameService service, int totalRounds) {
        GameSession session = service.createGame(totalRounds);
        session.status = GameStatus.IN_PROGRESS;
        return session;
    }

    // ---------- No repetición ----------

    @Test
    void noQuestionIsRepeatedWithinTheSameGame() {
        fakeRepo.add("FÚTBOL", 1L, 2L, 3L, 4L, 5L);
        fakeRepo.add("CIENCIA", 10L, 20L, 30L, 40L, 50L);
        fakeRepo.add("HISTORIA", 100L, 200L, 300L, 400L, 500L);
        fakeRepo.add("ANIMALES", 1000L, 2000L, 3000L, 4000L, 5000L);

        GameService service = serviceWithSeed(42);
        GameSession session = newInProgressGame(service, 10);

        Set<Long> seen = new HashSet<>();
        for (int round = 1; round <= 10; round++) {
            GameService.SelectedQuestion sel = service.selectNextQuestion(session).orElseThrow();
            assertEquals(round, sel.round(), "la ronda debe avanzar en orden");
            assertEquals(round, session.currentRound);
            assertTrue(seen.add(sel.questionId()), "pregunta repetida en la partida: " + sel.questionId());
            assertTrue(session.usedQuestionIds.contains(sel.questionId()));
        }
        assertEquals(10, session.usedQuestionIds.size());
        assertEquals(10, seen.size());
        assertFalse(session.insufficientQuestions);
    }

    @Test
    void selectedQuestionAlwaysBelongsToTheSelectedCategory() {
        fakeRepo.add("CAT_A", 1L, 2L, 3L, 4L, 5L);
        fakeRepo.add("CAT_B", 10L, 11L, 12L, 13L, 14L);
        fakeRepo.add("CAT_C", 100L, 101L, 102L, 103L, 104L);

        GameService service = serviceWithSeed(7);
        GameSession session = newInProgressGame(service, 10);

        for (int round = 1; round <= 10; round++) {
            GameService.SelectedQuestion sel = service.selectNextQuestion(session).orElseThrow();
            assertTrue(fakeRepo.idsOf(sel.category()).contains(sel.questionId()),
                    "la pregunta " + sel.questionId() + " no pertenece a " + sel.category());
        }
    }

    // ---------- Categorías agotadas ----------

    @Test
    void categoryWithNoRemainingQuestionsIsNoLongerSelected() {
        fakeRepo.add("RARA", 1L); // una única pregunta en esta categoría
        for (long i = 10; i < 40; i++) {
            fakeRepo.add("COMÚN", i);
        }

        GameService service = serviceWithSeed(3);
        GameSession session = newInProgressGame(service, 5);

        int rarePicks = 0;
        for (int round = 1; round <= 5; round++) {
            GameService.SelectedQuestion sel = service.selectNextQuestion(session).orElseThrow();
            if ("RARA".equals(sel.category())) {
                rarePicks++;
            } else {
                assertEquals("COMÚN", sel.category());
            }
        }
        assertTrue(rarePicks <= 1, "RARA tiene una sola pregunta: no puede salir más de una vez");
    }

    // ---------- Límite: menos preguntas que rondas ----------

    @Test
    void gameWithMoreRoundsThanAvailableQuestionsEndsEarlyWithNotice() {
        // Catálogo mínimo: una pregunta por categoría (como la BD inicial)
        fakeRepo.add("FÚTBOL", 1L);
        fakeRepo.add("MATEMÁTICAS", 2L);
        fakeRepo.add("CIENCIA", 3L);
        fakeRepo.add("HISTORIA", 4L);
        fakeRepo.add("CULTURA GENERAL", 5L);
        fakeRepo.add("GEOGRAFÍA", 6L);
        fakeRepo.add("ANIMALES", 7L);
        fakeRepo.add("TECNOLOGÍA", 8L);

        GameService service = serviceWithSeed(11);
        GameSession session = newInProgressGame(service, 10); // partida de 10 con solo 8 preguntas

        Set<Long> seen = new HashSet<>();
        Optional<GameService.SelectedQuestion> selection;
        int roundsPlayed = 0;
        while ((selection = service.selectNextQuestion(session)).isPresent()) {
            assertTrue(seen.add(selection.get().questionId()), "no debe repetirse ninguna pregunta");
            roundsPlayed++;
        }

        assertEquals(8, roundsPlayed, "solo se juegan las 8 preguntas únicas existentes");
        assertEquals(8, session.currentRound);
        assertEquals(8, session.usedQuestionIds.size());
        assertTrue(session.insufficientQuestions,
                "debe informarse que no existen suficientes preguntas disponibles");
    }

    // ---------- Aleatoriedad real ----------

    @Test
    void categoriesAreNotChosenInAFixedOrder() {
        for (int i = 0; i < 50; i++) {
            fakeRepo.add("CAT_A", 100L + i);
            fakeRepo.add("CAT_B", 200L + i);
        }

        GameService service = serviceUnseeded();
        int catAPicks = 0;
        int trials = 400;
        for (int t = 0; t < trials; t++) {
            GameSession session = newInProgressGame(service, 5);
            String category = service.selectNextQuestion(session).orElseThrow().category();
            if ("CAT_A".equals(category)) {
                catAPicks++;
            }
        }
        // 400 sorteos uniformes: se espera ~200 por categoría. Salirse de
        // [120, 280] es prácticamente imposible si el sorteo es aleatorio.
        assertTrue(catAPicks > 120 && catAPicks < 280,
                "la distribución entre categorías parece no aleatoria: " + catAPicks + "/" + trials);
    }

    @Test
    void questionsInsideACategoryVaryAcrossGames() {
        for (int i = 0; i < 30; i++) {
            fakeRepo.add("ÚNICA", 5_000L + i);
        }

        GameService service = serviceUnseeded();
        Set<Long> firstQuestions = new HashSet<>();
        for (int t = 0; t < 150; t++) {
            GameSession session = newInProgressGame(service, 5);
            firstQuestions.add(service.selectNextQuestion(session).orElseThrow().questionId());
        }
        assertTrue(firstQuestions.size() >= 5,
                "la primera pregunta de cada partida debería variar; solo se vieron "
                        + firstQuestions.size() + " distintas");
    }

    // ---------- Precondiciones ----------

    @Test
    void selectNextQuestionRejectsWhenGameIsNotInProgress() {
        fakeRepo.add("CAT", 1L, 2L);
        GameService service = serviceWithSeed(1);

        GameSession lobby = service.createGame(5); // sigue en LOBBY
        assertThrows(IllegalArgumentException.class, () -> service.selectNextQuestion(lobby));

        lobby.status = GameStatus.FINISHED;
        assertThrows(IllegalArgumentException.class, () -> service.selectNextQuestion(lobby));
    }

    @Test
    void selectNextQuestionRejectsAfterAllRoundsAreComplete() {
        fakeRepo.add("CAT", 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);

        GameService service = serviceWithSeed(2);
        GameSession session = newInProgressGame(service, 5);
        for (int round = 1; round <= 5; round++) {
            service.selectNextQuestion(session).orElseThrow();
        }
        assertEquals(5, session.currentRound);
        assertThrows(IllegalArgumentException.class, () -> service.selectNextQuestion(session));
    }
}
