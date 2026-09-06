package com.example.quiz.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración del FLUJO COMPLETO de la partida (FASE 9) a nivel del
 * motor de juego: reproduce el escenario jugador por jugador con 3 jugadores,
 * 5 rondas, categoría/pregunta aleatorias sin repetición, una ronda cerrada
 * por el temporizador (simulado con reloj manual, sin esperar 15 s reales),
 * puntuación con speed bonus, ranking final y limpieza de la partida.
 *
 * <p>Determinista: usa repositorio simulado, semilla fija, reloj y
 * temporizador de prueba. No requiere red ni Docker.
 */
class FullGameFlowTest {

    private FakeQuestionRepository fakeRepo;
    private RecordingBroadcaster recorder;
    private FakeRoundScheduler scheduler;
    private MutableTimeProvider clock;
    private GameService service;
    private GameSession session;
    private Player ana;
    private Player beto;
    private Player carla;

    private static final int TIMEOUT_ROUND = 3;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(WsMessage message) {
        return (Map<String, Object>) message.payload();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> answerList(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("answers");
    }

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeQuestionRepository();
        fakeRepo.add("FÚTBOL", 1L, 2L);
        fakeRepo.add("CIENCIA", 10L, 11L);
        fakeRepo.add("HISTORIA", 100L, 101L);
        fakeRepo.add("ANIMALES", 1000L, 1001L);
        fakeRepo.add("GEOGRAFÍA", 2000L, 2001L);
        fakeRepo.add("MATEMÁTICAS", 3000L, 3001L); // 12 preguntas para 5 rondas

        recorder = new RecordingBroadcaster();
        scheduler = new FakeRoundScheduler();
        clock = new MutableTimeProvider(0);
        service = new GameService(fakeRepo, new Random(42), recorder, scheduler, clock);

        session = service.createGame(5);
        ana = service.joinGame(session.gameId, "Ana");
        beto = service.joinGame(session.gameId, "Beto");
        carla = service.joinGame(session.gameId, "Carla");
    }

    private long currentQuestionId() {
        Map<String, Object> payload = payload(recorder.lastOfType(GameEvents.NEW_QUESTION));
        return ((Number) payload.get("questionId")).longValue();
    }

    private String correctOptionOfCurrentQuestion() {
        return fakeRepo.findActiveQuestionById(currentQuestionId()).orElseThrow().correctOption;
    }

    private String wrongOption(String correct) {
        return correct.equals("A") ? "B" : "A";
    }

    @Test
    void fullGameFlowWithThreePlayersEndsWithRankingAndCleanup() {
        // ---- Inicio: A empieza la partida ----
        service.startGame(session.gameId, ana.playerId);

        Map<String, Object> started = payload(recorder.lastOfType(GameEvents.GAME_STARTED));
        assertEquals(3, ((List<?>) started.get("players")).size());
        assertEquals(GameStatus.IN_PROGRESS, session.status);

        Set<Long> seenQuestions = new HashSet<>();
        int correctByAna = 0;
        int correctByCarla = 0;

        for (int round = 1; round <= session.totalRounds; round++) {
            // ---- Cada ronda: pregunta aleatoria, distinta y sin respuesta ----
            Map<String, Object> question = payload(recorder.lastOfType(GameEvents.NEW_QUESTION));
            assertEquals(round, question.get("round"));
            assertFalse(question.containsKey("correctOption"), "la pregunta nunca trae la respuesta");
            long questionId = ((Number) question.get("questionId")).longValue();
            assertTrue(seenQuestions.add(questionId), "no puede repetirse la pregunta " + questionId);
            assertTrue(question.containsKey("optionA") && question.containsKey("optionD"));
            assertEquals(15_000L, question.get("timeLimitMs"));

            String correct = correctOptionOfCurrentQuestion();

            // ---- Respuestas ----
            if (round == TIMEOUT_ROUND) {
                // Solo Ana responde: Beto y Carla dejan vencer el temporizador
                service.submitAnswer(session.gameId, ana.playerId, correct);
                clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
                assertTrue(scheduler.fire(scheduler.lastHandle()),
                        "el temporizador de la ronda 3 debe estar programado");
            } else {
                service.submitAnswer(session.gameId, ana.playerId, correct);
                service.submitAnswer(session.gameId, beto.playerId, wrongOption(correct));
                service.submitAnswer(session.gameId, carla.playerId, correct);
            }

            // ---- Resultado de la ronda ----
            Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
            assertEquals(round, result.get("round"));
            assertEquals(correct, result.get("correctOption"));

            List<Map<String, Object>> answers = answerList(result);
            assertEquals(3, answers.size());

            for (Map<String, Object> answer : answers) {
                String nickname = (String) answer.get("nickname");
                boolean isCorrect = (boolean) answer.get("correct");
                int points = (int) answer.get("points");
                switch (nickname) {
                    case "Ana" -> {
                        assertTrue(isCorrect, "Ana responde siempre correcto");
                        // Al responder con el reloj congelado: 100 + 20 de bonus = 120
                        assertEquals(120, points);
                        correctByAna++;
                    }
                    case "Beto" -> {
                        assertFalse(isCorrect, "Beto responde siempre incorrecto");
                        assertEquals(0, points);
                    }
                    case "Carla" -> {
                        if (round == TIMEOUT_ROUND) {
                            assertFalse(isCorrect, "Carla no responde en la ronda 3");
                            assertEquals("", answer.get("selectedOption"));
                            assertEquals(0, points);
                        } else {
                            assertTrue(isCorrect, "Carla responde correcto salvo el timeout");
                            assertEquals(120, points);
                            correctByCarla++;
                        }
                    }
                    default -> throw new IllegalStateException("Jugador inesperado: " + nickname);
                }
            }

            if (round < session.totalRounds) {
                assertEquals(round + 1, payload(recorder.lastOfType(GameEvents.NEXT_QUESTION)).get("round"));
            }
        }

        // ---- Puntuaciones esperadas: 5 x 120 y 4 x 120 ----
        assertEquals(5 * 120, ana.score);
        assertEquals(0, beto.score);
        assertEquals(4 * 120, carla.score);
        assertEquals(5, correctByAna);
        assertEquals(4, correctByCarla);
        assertEquals(5, seenQuestions.size());

        // ---- Fin de partida: ranking ordenado y limpieza ----
        Map<String, Object> finished = payload(recorder.lastOfType(GameEvents.GAME_FINISHED));
        assertEquals(false, finished.get("insufficientQuestions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) finished.get("ranking");
        assertEquals(3, ranking.size());
        assertEquals("Ana", ranking.get(0).get("nickname"));
        assertEquals(600, ranking.get(0).get("score"));
        assertEquals("Carla", ranking.get(1).get("nickname"));
        assertEquals(480, ranking.get(1).get("score"));
        assertEquals("Beto", ranking.get(2).get("nickname"));
        assertEquals(0, ranking.get(2).get("score"));

        // La partida terminada se elimina de memoria
        assertTrue(service.findGame(session.gameId).isEmpty());

        // ---- Resumen de eventos ----
        assertEquals(5, recorder.countOfType(GameEvents.NEW_QUESTION));
        assertEquals(5, recorder.countOfType(GameEvents.QUESTION_RESULT));
        assertEquals(4, recorder.countOfType(GameEvents.NEXT_QUESTION));
        assertEquals(1, recorder.countOfType(GameEvents.GAME_FINISHED));
    }
}
