package com.example.quiz.game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la modalidad LOCAL (misma pantalla, por turnos): cada pregunta la
 * responde UN jugador en rotación por orden de la sala, el servidor anuncia
 * de quién es el turno en NEW_QUESTION (campo "player") y la ronda se cierra
 * y califica al instante al responder. Las respuestas fuera de turno se
 * rechazan. El modo ONLINE no debe cambiar su comportamiento.
 */
class LocalTurnModeTest {

    private FakeQuestionRepository fakeRepo;
    private RecordingBroadcaster recorder;
    private FakeRoundScheduler scheduler;
    private MutableTimeProvider clock;
    private GameService service;
    private GameSession session;
    private Player ana;
    private Player luis;
    private Player mia;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payload(WsMessage message) {
        return (Map<String, Object>) message.payload();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> answerList(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("answers");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> turnPlayer(Map<String, Object> question) {
        return (Map<String, Object>) question.get("player");
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

        session = service.createGame(5, GameMode.LOCAL);
        ana = service.joinGame(session.gameId, "Ana");
        luis = service.joinGame(session.gameId, "Luis");
        mia = service.joinGame(session.gameId, "Mia");
    }

    private Map<String, Object> currentQuestion() {
        return payload(recorder.lastOfType(GameEvents.NEW_QUESTION));
    }

    private void start() {
        service.startGame(session.gameId, ana.playerId);
    }

    /** El jugador del turno responde; las preguntas de relleno son correctas con "A". */
    private void answerTurnWith(String option) {
        Map<String, Object> player = turnPlayer(currentQuestion());
        service.submitAnswer(session.gameId, (String) player.get("playerId"), option);
    }

    @Test
    void gameStartedInLocalModeAnnouncesTurnAndGameFinishedAfterRotation() {
        start();

        Map<String, Object> started = payload(recorder.lastOfType(GameEvents.GAME_STARTED));
        assertEquals("LOCAL", started.get("mode"));
        assertEquals(3, ((List<?>) started.get("players")).size());

        // Rotación por orden de la sala: Ana, Luis, Mia, Ana, Luis
        String[] expectedOrder = {"Ana", "Luis", "Mia", "Ana", "Luis"};

        for (int round = 1; round <= session.totalRounds; round++) {
            Map<String, Object> question = currentQuestion();
            assertEquals(round, question.get("round"));
            assertFalse(question.containsKey("correctOption"), "la pregunta nunca trae la respuesta");

            Map<String, Object> player = turnPlayer(question);
            assertNotNull(player, "en LOCAL NEW_QUESTION anuncia al jugador del turno");
            assertEquals(expectedOrder[round - 1], player.get("nickname"));

            // Responde el jugador del turno, siempre correcto ("A")
            answerTurnWith("A");

            // La ronda se cierra al instante: calificación inmediata
            Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
            assertEquals(round, result.get("round"));
            assertEquals("A", result.get("correctOption"));
            List<Map<String, Object>> answers = answerList(result);
            assertEquals(1, answers.size(), "solo se califica al jugador del turno");
            assertEquals(player.get("playerId"), answers.get(0).get("playerId"));
            assertEquals(true, answers.get(0).get("correct"));
            assertEquals(120, answers.get(0).get("points")); // 100 + 20 de bonus (reloj congelado)
        }

        // Puntuaciones: Ana (rondas 1 y 4), Luis (2 y 5), Mia (ronda 3)
        assertEquals(240, ana.score);
        assertEquals(240, luis.score);
        assertEquals(120, mia.score);

        // Ranking final ordenado y limpieza de la partida
        Map<String, Object> finished = payload(recorder.lastOfType(GameEvents.GAME_FINISHED));
        assertEquals(false, finished.get("insufficientQuestions"));
        List<Map<String, Object>> ranking = (List<Map<String, Object>>) finished.get("ranking");
        assertEquals(3, ranking.size());
        assertEquals("Ana", ranking.get(0).get("nickname"));
        assertEquals("Luis", ranking.get(1).get("nickname"));
        assertEquals("Mia", ranking.get(2).get("nickname"));
        assertTrue(service.findGame(session.gameId).isEmpty());
    }

    @Test
    void answerFromPlayerOutOfTurnIsRejectedAndRoundStaysOpen() {
        start();
        Map<String, Object> player = turnPlayer(currentQuestion());
        assertEquals("Ana", player.get("nickname"), "la ronda 1 es de Ana");

        // Luis intenta responder cuando le toca a Ana
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submitAnswer(session.gameId, luis.playerId, luis.playerId, "A"));
        assertTrue(error.getMessage().contains("le toca a Ana"));

        // La ronda sigue abierta y Ana aún puede responder
        assertEquals(0, recorder.countOfType(GameEvents.QUESTION_RESULT));
        answerTurnWith("A");
        assertEquals(1, recorder.countOfType(GameEvents.QUESTION_RESULT));
        assertEquals(120, ana.score);
    }

    @Test
    void wrongAnswerIsGradedIncorrectImmediately() {
        start();
        Map<String, Object> player = turnPlayer(currentQuestion());
        assertEquals("Ana", player.get("nickname"));

        answerTurnWith("B"); // incorrecto (las preguntas de relleno responden "A")

        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        List<Map<String, Object>> answers = answerList(result);
        assertEquals(1, answers.size());
        assertEquals("Ana", answers.get(0).get("nickname"));
        assertEquals("B", answers.get(0).get("selectedOption"));
        assertEquals(false, answers.get(0).get("correct"));
        assertEquals(0, answers.get(0).get("points"));
        assertEquals(0, ana.score);
        assertEquals("A", result.get("correctOption"), "el resultado revela la respuesta correcta");
    }

    @Test
    void noAnswerInTimeClosesRoundAndNextTurnPlays() {
        start();
        assertEquals("Ana", turnPlayer(currentQuestion()).get("nickname"));

        // Nadie responde: vence el temporizador de 15 s
        clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
        assertTrue(scheduler.fire(scheduler.lastHandle()));

        // Se califica a Ana como sin respuesta (ella era la única de la ronda)
        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        List<Map<String, Object>> answers = answerList(result);
        assertEquals(1, answers.size());
        assertEquals("Ana", answers.get(0).get("nickname"));
        assertEquals("", answers.get(0).get("selectedOption"));
        assertEquals(false, answers.get(0).get("correct"));
        assertEquals(0, answers.get(0).get("points"));

        // El turno pasa a Luis en la ronda 2
        Map<String, Object> next = currentQuestion();
        assertEquals(2, next.get("round"));
        assertEquals("Luis", turnPlayer(next).get("nickname"));
    }

    @Test
    void onlineModeIsUnchangedAndHasNoTurnAnnouncement() {
        // Partida ONLINE en el mismo service (modalidad por defecto)
        GameSession online = service.createGame(5);
        Player a = service.joinGame(online.gameId, "Ana");
        Player b = service.joinGame(online.gameId, "Luis");
        Player c = service.joinGame(online.gameId, "Mia");
        service.startGame(online.gameId, a.playerId);

        Map<String, Object> started = payload(recorder.lastOfType(GameEvents.GAME_STARTED));
        assertEquals("ONLINE", started.get("mode"));

        Map<String, Object> question = payload(recorder.lastOfType(GameEvents.NEW_QUESTION));
        assertFalse(question.containsKey("player"), "ONLINE no anuncia turnos");

        // Todos responden la misma pregunta y la ronda cierra cuando responden todos
        service.submitAnswer(online.gameId, a.playerId, "A");
        service.submitAnswer(online.gameId, b.playerId, "B");
        service.submitAnswer(online.gameId, c.playerId, "A");

        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        assertEquals(3, answerList(result).size(), "ONLINE califica a todos los jugadores");
        assertTrue(service.findGame(online.gameId).isPresent());
    }
}
