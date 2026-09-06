package com.example.quiz.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la FASE 7: el servidor como autoridad del tiempo (cierre
 * automático a los 15 s, rechazo de respuestas tardías) y la puntuación
 * con speed bonus. Usan reloj y temporizador de prueba: no se esperan 15 s.
 *
 * <p>Fórmula: correcta = 100 + round(20 × remainingMs / 15000); sin
 * respuesta o incorrecta = 0.
 */
class GameTimerScoringTest {

    private FakeQuestionRepository fakeRepo;
    private RecordingBroadcaster recorder;
    private FakeRoundScheduler scheduler;
    private MutableTimeProvider clock;
    private GameService service;
    private GameSession session;
    private Player ana;
    private Player beto;

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
        fakeRepo.add("CAT_A", 1L, 2L, 3L, 4L, 5L, 6L);
        fakeRepo.add("CAT_B", 10L, 11L, 12L, 13L, 14L, 15L);

        recorder = new RecordingBroadcaster();
        scheduler = new FakeRoundScheduler();
        clock = new MutableTimeProvider(0);
        service = new GameService(fakeRepo, new Random(5), recorder, scheduler, clock);
        session = service.createGame(5);
        ana = service.joinGame(session.gameId, "Ana");
        beto = service.joinGame(session.gameId, "Beto");
    }

    private String lastQuestionId() {
        return String.valueOf(payload(recorder.lastOfType(GameEvents.NEW_QUESTION)).get("questionId"));
    }

    private String correctOption() {
        return fakeRepo.findActiveQuestionById(Long.valueOf(lastQuestionId())).orElseThrow().correctOption;
    }

    private void answer(String playerId, String option) {
        service.submitAnswer(session.gameId, playerId, option);
    }

    // ---------- Speed bonus ----------

    @Test
    void correctAnswerAtOnceGetsFullSpeedBonus() {
        service.startGame(session.gameId, ana.playerId);
        clock.advance(100); // responden 100 ms después de emitir la pregunta

        answer(ana.playerId, correctOption());
        answer(beto.playerId, correctOption()); // cierra la ronda

        List<Map<String, Object>> answers = answerList(payload(recorder.lastOfType(GameEvents.QUESTION_RESULT)));
        for (Map<String, Object> a : answers) {
            assertEquals(true, a.get("correct"));
            assertEquals(120, a.get("points"), "100 base + 20 de bonus por responder casi al instante");
            assertEquals(120, a.get("score"));
        }
        assertEquals(120, ana.score);
        assertEquals(120, beto.score);
    }

    @Test
    void bonusIsHalvedWhenAnsweringHalfwayThroughTheTimer() {
        service.startGame(session.gameId, ana.playerId);
        clock.advance(7_500); // quedan ~7500 ms: bonus 10

        answer(ana.playerId, correctOption());
        answer(beto.playerId, correctOption());

        List<Map<String, Object>> answers = answerList(payload(recorder.lastOfType(GameEvents.QUESTION_RESULT)));
        for (Map<String, Object> a : answers) {
            assertEquals(110, a.get("points"), "100 base + 10 de bonus a mitad de tiempo");
        }
    }

    @Test
    void bonusIsNearlyZeroWhenAnsweringAtTheVeryEnd() {
        service.startGame(session.gameId, ana.playerId);
        clock.advance(14_000); // quedan ~1000 ms: bonus round(20*1000/15000)=1

        answer(ana.playerId, correctOption());
        answer(beto.playerId, correctOption());

        List<Map<String, Object>> answers = answerList(payload(recorder.lastOfType(GameEvents.QUESTION_RESULT)));
        for (Map<String, Object> a : answers) {
            assertEquals(101, a.get("points"));
        }
    }

    @Test
    void wrongOrMissingAnswersGetZeroPoints() {
        service.startGame(session.gameId, ana.playerId);
        String wrong = correctOption().equals("A") ? "B" : "A";

        answer(ana.playerId, wrong);
        // Beto no responde: el temporizador cierra la ronda
        clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
        assertTrue(scheduler.fire(scheduler.lastHandle()), "el temporizador debe estar programado");

        List<Map<String, Object>> answers = answerList(payload(recorder.lastOfType(GameEvents.QUESTION_RESULT)));
        Map<String, Object> anaAnswer = answers.stream().filter(a -> a.get("nickname").equals("Ana")).findFirst().orElseThrow();
        Map<String, Object> betoAnswer = answers.stream().filter(a -> a.get("nickname").equals("Beto")).findFirst().orElseThrow();
        assertEquals(false, anaAnswer.get("correct"));
        assertEquals(0, anaAnswer.get("points"));
        assertEquals(false, betoAnswer.get("correct"));
        assertEquals("", betoAnswer.get("selectedOption"), "quien no respondió no selecciona opción");
        assertEquals(0, betoAnswer.get("points"));
    }

    // ---------- Autoridad del tiempo ----------

    @Test
    void answerAfterTheFifteenSecondsIsRejectedEvenBeforeTheTimerFires() {
        service.startGame(session.gameId, ana.playerId);
        clock.advance(GameService.QUESTION_TIME_LIMIT_MS + 1);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> answer(ana.playerId, correctOption()));
        assertTrue(ex.getMessage().contains("tiempo"), ex.getMessage());
        assertEquals(0, ana.score);
    }

    @Test
    void timerAutomaticallyClosesTheRoundWhenNobodyAnswers() {
        service.startGame(session.gameId, ana.playerId);

        clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
        assertTrue(scheduler.fire(scheduler.lastHandle()));

        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        assertEquals(1, result.get("round"));
        assertEquals(0, ana.score);
        assertEquals(0, beto.score);

        // La siguiente ronda arranca sola
        assertEquals(1, recorder.countOfType(GameEvents.NEXT_QUESTION));
        assertEquals(2, payload(recorder.lastOfType(GameEvents.NEW_QUESTION)).get("round"));
    }

    @Test
    void aStaleTimerFromAClosedRoundIsIgnored() {
        service.startGame(session.gameId, ana.playerId);
        long firstRoundTimer = scheduler.lastHandle();

        // Ambos responden al instante: la ronda se cierra sola y cancela su timer
        answer(ana.playerId, correctOption());
        answer(beto.playerId, correctOption());
        assertEquals(1, recorder.countOfType(GameEvents.QUESTION_RESULT));

        // Disparar el temporizador antiguo (cancelado) no produce otra ronda
        clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
        assertFalse(scheduler.fire(firstRoundTimer), "el temporizador cancelado no debe dispararse");
        assertEquals(1, recorder.countOfType(GameEvents.QUESTION_RESULT),
                "no puede cerrarse dos veces la misma ronda");
    }

    // ---------- Fin de partida por tiempo en la última ronda ----------

    @Test
    void timeoutOnTheLastRoundFinishesTheGameWithRanking() {
        // Jugamos 4 de las 5 rondas y dejamos vencer la última
        service.startGame(session.gameId, ana.playerId);
        for (int round = 1; round <= 4; round++) {
            answer(ana.playerId, correctOption());
            answer(beto.playerId, correctOption());
        }

        clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
        assertTrue(scheduler.fire(scheduler.lastHandle()));

        Map<String, Object> finished = payload(recorder.lastOfType(GameEvents.GAME_FINISHED));
        assertEquals(false, finished.get("insufficientQuestions"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) finished.get("ranking");
        assertEquals(2, ranking.size());
        // Ana y Beto respondieron bien 4 rondas al instante: 4 x 120
        assertEquals(480, ranking.get(0).get("score"));
        assertEquals(480, ranking.get(1).get("score"));
        assertTrue(service.findGame(session.gameId).isEmpty());
    }
}
