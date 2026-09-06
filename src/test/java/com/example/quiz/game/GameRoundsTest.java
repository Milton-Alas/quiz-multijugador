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
 * Tests de la FASE 6 (y FASE 7 en lo que afecta a los puntos): orquestación
 * de rondas por WebSocket sobre el GameService, con broadcaster, temporizador
 * y reloj de prueba (sin contenedor WS ni base de datos).
 *
 * <p>Puntos esperados por respuesta correcta inmediata: 100 + 20 (bonus) = 120.
 */
class GameRoundsTest {

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
        // 4 categorías x 6 preguntas = 24 preguntas disponibles
        fakeRepo.add("FÚTBOL", 1L, 2L, 3L, 4L, 5L, 6L);
        fakeRepo.add("CIENCIA", 10L, 11L, 12L, 13L, 14L, 15L);
        fakeRepo.add("HISTORIA", 100L, 101L, 102L, 103L, 104L, 105L);
        fakeRepo.add("ANIMALES", 1000L, 1001L, 1002L, 1003L, 1004L, 1005L);

        recorder = new RecordingBroadcaster();
        scheduler = new FakeRoundScheduler();
        clock = new MutableTimeProvider(0);
        service = new GameService(fakeRepo, new Random(7), recorder, scheduler, clock);
        session = service.createGame(5);
        ana = service.joinGame(session.gameId, "Ana");
        beto = service.joinGame(session.gameId, "Beto");
    }

    /** Id de la pregunta de la última NEW_QUESTION emitida. */
    private long lastQuestionId() {
        return ((Number) payload(recorder.lastOfType(GameEvents.NEW_QUESTION)).get("questionId")).longValue();
    }

    private String correctOption(long questionId) {
        return fakeRepo.findActiveQuestionById(questionId).orElseThrow().correctOption;
    }

    private void answerCorrect(String playerId) {
        service.submitAnswer(session.gameId, playerId, correctOption(lastQuestionId()));
    }

    // ---------- Inicio de partida y nueva pregunta ----------

    @Test
    void startGameBroadcastsGameStartedAndFirstQuestionWithoutTheAnswer() {
        service.startGame(session.gameId, ana.playerId);

        Map<String, Object> started = payload(recorder.lastOfType(GameEvents.GAME_STARTED));
        assertEquals(5, started.get("totalRounds"));
        assertTrue(started.get("players") instanceof List, "GAME_STARTED incluye la lista de jugadores");

        Map<String, Object> question = payload(recorder.lastOfType(GameEvents.NEW_QUESTION));
        assertEquals(1, question.get("round"));
        assertTrue(question.containsKey("question"));
        assertTrue(question.containsKey("optionA"));
        assertTrue(question.containsKey("optionD"));
        assertTrue(question.containsKey("timeLimitMs"));
        assertEquals(15_000L, question.get("timeLimitMs"));
        assertFalse(question.containsKey("correctOption"),
                "NEW_QUESTION NUNCA debe incluir la respuesta correcta");
        assertEquals(GameStatus.IN_PROGRESS, session.status);
    }

    @Test
    void startGameRequiresThePlayerToBePartOfTheGame() {
        GameSession empty = service.createGame(5);
        assertThrows(IllegalArgumentException.class,
                () -> service.startGame(empty.gameId, "jugador-inexistente"));
    }

    // ---------- Rondas: respuestas, resultado, siguiente pregunta ----------

    @Test
    void fullGameOfTwoPlayersAnsweringCorrectEndsWithRanking() {
        service.startGame(session.gameId, ana.playerId);

        int totalRounds = session.totalRounds;
        for (int round = 1; round <= totalRounds; round++) {
            answerCorrect(ana.playerId);
            answerCorrect(beto.playerId);
        }

        assertEquals(totalRounds, recorder.countOfType(GameEvents.NEW_QUESTION));
        assertEquals(totalRounds, recorder.countOfType(GameEvents.QUESTION_RESULT));
        assertEquals(totalRounds - 1, recorder.countOfType(GameEvents.NEXT_QUESTION));
        assertEquals(1, recorder.countOfType(GameEvents.GAME_FINISHED));

        // Puntos: (100 + 20 de bonus) x 5 rondas, respondiendo al instante
        assertEquals(600, ana.score);
        assertEquals(600, beto.score);

        Map<String, Object> finished = payload(recorder.lastOfType(GameEvents.GAME_FINISHED));
        assertEquals(false, finished.get("insufficientQuestions"));
        assertTrue(finished.get("message").toString().contains("Partida terminada"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) finished.get("ranking");
        assertEquals(2, ranking.size());
        assertEquals(600, ranking.get(0).get("score"));
        assertEquals(600, ranking.get(1).get("score"));

        // La partida finalizada se elimina de memoria
        assertTrue(service.findGame(session.gameId).isEmpty());
    }

    @Test
    void roundResultRevealsAnswerAndAwardsPoints() {
        service.startGame(session.gameId, ana.playerId);
        long questionId = lastQuestionId();
        String correct = correctOption(questionId);
        String wrong = correct.equals("A") ? "B" : "A";

        service.submitAnswer(session.gameId, ana.playerId, correct);
        service.submitAnswer(session.gameId, beto.playerId, wrong); // cierra la ronda

        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        assertEquals(1, result.get("round"));
        assertEquals(correct, result.get("correctOption"));
        assertEquals(120, ana.score);
        assertEquals(0, beto.score);

        List<Map<String, Object>> answers = answerList(result);
        assertEquals(2, answers.size());

        Map<String, Object> anaAnswer = answers.stream()
                .filter(a -> a.get("nickname").equals("Ana")).findFirst().orElseThrow();
        assertEquals(true, anaAnswer.get("correct"));
        assertEquals(120, anaAnswer.get("points"));
        assertEquals(120, anaAnswer.get("score"));

        Map<String, Object> betoAnswer = answers.stream()
                .filter(a -> a.get("nickname").equals("Beto")).findFirst().orElseThrow();
        assertEquals(false, betoAnswer.get("correct"));
        assertEquals(0, betoAnswer.get("points"));
        assertEquals(0, betoAnswer.get("score"));
        assertEquals(wrong, betoAnswer.get("selectedOption"));

        // Tras el resultado y con rondas restantes, llega la siguiente pregunta
        assertEquals(1, recorder.countOfType(GameEvents.NEXT_QUESTION));
        assertEquals(2, payload(recorder.lastOfType(GameEvents.NEW_QUESTION)).get("round"));
    }

    // ---------- Respuestas duplicadas ----------

    @Test
    void duplicateAnswersAreIgnoredAndDoNotAdvanceTheRoundEarly() {
        service.startGame(session.gameId, ana.playerId);
        String correct = correctOption(lastQuestionId());

        service.submitAnswer(session.gameId, ana.playerId, correct);
        service.submitAnswer(session.gameId, ana.playerId, "B"); // duplicada, se ignora

        assertEquals(1, recorder.countOfType(GameEvents.PLAYER_ANSWERED));
        assertEquals(0, recorder.countOfType(GameEvents.QUESTION_RESULT),
                "la ronda no debe cerrarse hasta que respondan todos los conectados");

        service.submitAnswer(session.gameId, beto.playerId, correct);
        assertEquals(1, recorder.countOfType(GameEvents.QUESTION_RESULT));
    }

    // ---------- Respuestas fuera de una pregunta activa ----------

    @Test
    void answerIsRejectedWhenThereIsNoActiveQuestionOrTheOptionIsInvalid() {
        // Partida en LOBBY (sin empezar): no hay pregunta activa
        assertThrows(IllegalArgumentException.class,
                () -> service.submitAnswer(session.gameId, ana.playerId, "A"));

        service.startGame(session.gameId, ana.playerId);
        // Opción fuera de A-D es inválida
        assertThrows(IllegalArgumentException.class,
                () -> service.submitAnswer(session.gameId, ana.playerId, "Z"));
    }

    // ---------- Falta de preguntas (termina antes) ----------

    @Test
    void gameWithMoreRoundsThanQuestionsFinishesEarlyWithNotice() {
        FakeQuestionRepository small = new FakeQuestionRepository();
        small.add("CAT1", 1L, 2L, 3L);
        small.add("CAT2", 4L, 5L, 6L);
        small.add("CAT3", 7L, 8L, 9L); // solo 9 preguntas para una partida de 10

        RecordingBroadcaster smallRecorder = new RecordingBroadcaster();
        FakeRoundScheduler smallScheduler = new FakeRoundScheduler();
        MutableTimeProvider smallClock = new MutableTimeProvider(0);
        GameService smallService = new GameService(small, new Random(3), smallRecorder, smallScheduler, smallClock);
        GameSession smallSession = smallService.createGame(10);
        Player only = smallService.joinGame(smallSession.gameId, "Solo");
        smallService.startGame(smallSession.gameId, only.playerId);

        // Se juegan exactamente las 9 preguntas únicas
        for (int round = 1; round <= 9; round++) {
            long id = ((Number) payload(smallRecorder.lastOfType(GameEvents.NEW_QUESTION))
                    .get("questionId")).longValue();
            smallService.submitAnswer(smallSession.gameId, only.playerId,
                    small.findActiveQuestionById(id).orElseThrow().correctOption);
        }

        assertEquals(9, smallRecorder.countOfType(GameEvents.QUESTION_RESULT));
        Map<String, Object> finished = payload(smallRecorder.lastOfType(GameEvents.GAME_FINISHED));
        assertEquals(true, finished.get("insufficientQuestions"));
        assertTrue(finished.get("message").toString().contains("suficientes"));
        assertEquals(9, smallSession.currentRound);
        assertEquals(9 * 120, only.score);
    }

    // ---------- Desconexión de un jugador ----------

    @Test
    void disconnectedPlayerDoesNotBlockTheRound() {
        service.startGame(session.gameId, ana.playerId);

        service.playerLeft(session.gameId, beto.playerId); // Beto se desconecta

        // Solo Ana responde y la ronda avanza igual
        answerCorrect(ana.playerId);
        assertEquals(1, recorder.countOfType(GameEvents.QUESTION_RESULT));
        assertEquals(1, recorder.countOfType(GameEvents.NEXT_QUESTION));

        Map<String, Object> left = payload(recorder.lastOfType(GameEvents.PLAYER_LEFT));
        assertEquals(beto.playerId, left.get("playerId"));
    }
}
