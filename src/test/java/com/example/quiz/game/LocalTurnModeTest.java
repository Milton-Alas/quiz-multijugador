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
 * Tests de la modalidad LOCAL (misma pantalla, por turnos).
 *
 * <p>Una "ronda" (ciclo) la juegan TODOS los jugadores por turnos y cada uno
 * responde SU PROPIA pregunta aleatoria distinta: ronda 1 -> Ana, Luis, Mia
 * (cada quien su pregunta); ronda 2 -> Ana, Luis, Mia; etc. Con N rondas
 * configuradas, cada jugador responde exactamente N preguntas. El servidor
 * anuncia de quién es el turno en NEW_QUESTION (campo "player", con "cycle"
 * y "turnOrder") y la respuesta se califica al instante. Las respuestas
 * fuera de turno se rechazan. El modo ONLINE no debe cambiar.
 */
class LocalTurnModeTest {

    private FakeQuestionRepository fakeRepo;
    private RecordingBroadcaster recorder;
    private FakeRoundScheduler scheduler;
    private MutableTimeProvider clock;
    private GameService service;
    private GameSession session;
    private List<Player> players; // Ana, Luis, Mia (orden de la sala)

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
        // 18 preguntas: suficientes para 5 rondas × 3 jugadores = 15 turnos
        for (String cat : List.of("FÚTBOL", "CIENCIA", "HISTORIA", "ANIMALES", "GEOGRAFÍA", "MATEMÁTICAS")) {
            long base = switch (cat) {
                case "FÚTBOL" -> 1L;
                case "CIENCIA" -> 10L;
                case "HISTORIA" -> 100L;
                case "ANIMALES" -> 1000L;
                case "GEOGRAFÍA" -> 2000L;
                default -> 3000L;
            };
            fakeRepo.add(cat, base, base + 1, base + 2);
        }

        recorder = new RecordingBroadcaster();
        scheduler = new FakeRoundScheduler();
        clock = new MutableTimeProvider(0);
        service = new GameService(fakeRepo, new Random(42), recorder, scheduler, clock);

        session = service.createGame(5, GameMode.LOCAL); // 5 rondas = 5 preguntas por jugador
        players = List.of(
                service.joinGame(session.gameId, "Ana"),
                service.joinGame(session.gameId, "Luis"),
                service.joinGame(session.gameId, "Mia"));
    }

    private Map<String, Object> currentQuestion() {
        return payload(recorder.lastOfType(GameEvents.NEW_QUESTION));
    }

    private void start() {
        service.startGame(session.gameId, players.get(0).playerId);
    }

    /** El jugador del turno responde; las preguntas de relleno son correctas con "A". */
    private void answerTurnWith(String option) {
        Map<String, Object> player = turnPlayer(currentQuestion());
        service.submitAnswer(session.gameId, (String) player.get("playerId"), option);
    }

    /** Turno global esperado para la vuelta 1..15: (vuelta-1)%3 = posición en la sala. */
    private Player expectedPlayerForTurn(int turn) {
        return players.get((turn - 1) % players.size());
    }

    @Test
    void eachRoundIsPlayedByEveryPlayerInOrderUntilGameEnds() {
        start();

        Map<String, Object> started = payload(recorder.lastOfType(GameEvents.GAME_STARTED));
        assertEquals("LOCAL", started.get("mode"));
        assertEquals(3, ((List<?>) started.get("players")).size());

        int playerCount = players.size();
        int totalTurns = playerCount * session.totalRounds; // 3 × 5 = 15
        int turnsPlayed = 0;

        while (turnsPlayed < totalTurns) {
            turnsPlayed++;
            Map<String, Object> question = currentQuestion();
            assertEquals(turnsPlayed, question.get("round"));
            assertFalse(question.containsKey("correctOption"), "la pregunta nunca trae la respuesta");

            // La ronda visible = ciclo; cada ciclo lo juegan todos una vez
            int expectedCycle = (turnsPlayed - 1) / playerCount + 1;
            int expectedOrder = (turnsPlayed - 1) % playerCount + 1;
            assertEquals(expectedCycle, question.get("cycle"), "ciclo en el turno " + turnsPlayed);
            assertEquals(expectedOrder, question.get("turnOrder"), "turno dentro del ciclo");

            Player expected = expectedPlayerForTurn(turnsPlayed);
            Map<String, Object> player = turnPlayer(question);
            assertNotNull(player, "en LOCAL NEW_QUESTION anuncia al jugador del turno");
            assertEquals(expected.playerId, player.get("playerId"));
            assertEquals(expected.nickname, player.get("nickname"));

            // El jugador del turno responde correcto ("A"): calificación instantánea
            answerTurnWith("A");
            Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
            assertEquals(turnsPlayed, result.get("round"));
            List<Map<String, Object>> answers = answerList(result);
            assertEquals(1, answers.size(), "solo se califica al jugador del turno");
            assertEquals(expected.playerId, answers.get(0).get("playerId"));
            assertEquals(true, answers.get(0).get("correct"));
            assertEquals(120, answers.get(0).get("points")); // 100 + 20 de bonus (reloj congelado)
        }

        // Cada jugador respondió 5 preguntas (una por ronda) -> 5 × 120 = 600
        for (Player player : players) {
            assertEquals(5 * 120, player.score, player.nickname + " debe sumar 600");
        }

        // Ranking final ordenado (empate a 600 se ordena por apodo) y limpieza
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
    void answerFromPlayerOutOfTurnIsRejectedAndTurnStaysOpen() {
        start();
        Map<String, Object> player = turnPlayer(currentQuestion());
        assertEquals("Ana", player.get("nickname"), "el primer turno de la ronda 1 es de Ana");

        // Luis intenta responder cuando le toca a Ana
        Player luis = players.get(1);
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.submitAnswer(session.gameId, luis.playerId, luis.playerId, "A"));
        assertTrue(error.getMessage().contains("le toca a Ana"));

        // El turno sigue abierto y Ana aún puede responder
        assertEquals(0, recorder.countOfType(GameEvents.QUESTION_RESULT));
        answerTurnWith("A");
        assertEquals(1, recorder.countOfType(GameEvents.QUESTION_RESULT));
        assertEquals(120, players.get(0).score);
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
        assertEquals(0, players.get(0).score);
        assertEquals("A", result.get("correctOption"), "el resultado revela la respuesta correcta");
    }

    @Test
    void noAnswerInTimeClosesTurnAndNextPlayerOfSameRoundPlays() {
        start();
        assertEquals("Ana", turnPlayer(currentQuestion()).get("nickname"));

        // Nadie responde: vence el temporizador de 15 s del turno de Ana
        clock.advance(GameService.QUESTION_TIME_LIMIT_MS);
        assertTrue(scheduler.fire(scheduler.lastHandle()));

        // Se califica a Ana como sin respuesta (ella era la única del turno)
        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        List<Map<String, Object>> answers = answerList(result);
        assertEquals(1, answers.size());
        assertEquals("Ana", answers.get(0).get("nickname"));
        assertEquals("", answers.get(0).get("selectedOption"));
        assertEquals(false, answers.get(0).get("correct"));
        assertEquals(0, answers.get(0).get("points"));

        // El turno siguiente es de Luis, DENTRO de la misma ronda (ciclo 1)
        Map<String, Object> next = currentQuestion();
        assertEquals(1, next.get("cycle"));
        assertEquals(2, next.get("turnOrder"));
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
        assertFalse(question.containsKey("cycle"), "ONLINE no usa ciclos");

        // Todos responden la misma pregunta y la ronda cierra cuando responden todos
        service.submitAnswer(online.gameId, a.playerId, "A");
        service.submitAnswer(online.gameId, b.playerId, "B");
        service.submitAnswer(online.gameId, c.playerId, "A");

        Map<String, Object> result = payload(recorder.lastOfType(GameEvents.QUESTION_RESULT));
        assertEquals(3, answerList(result).size(), "ONLINE califica a todos los jugadores");
        assertTrue(service.findGame(online.gameId).isPresent());
    }
}
