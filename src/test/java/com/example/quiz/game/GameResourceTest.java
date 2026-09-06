package com.example.quiz.game;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests REST de la FASE 5 sobre el flujo crear partida - unirse - consultar.
 * La base de datos (H2) no se usa en estas operaciones: el estado de las
 * partidas vive en memoria.
 */
@QuarkusTest
class GameResourceTest {

    private String createGame(int totalRounds) {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"totalRounds\": " + totalRounds + "}")
                .when().post("/api/games")
                .then().statusCode(201)
                .extract().jsonPath().getString("gameId");
    }

    @Test
    void createGameReturnsPublicGameInLobby() {
        String body = given()
                .contentType(ContentType.JSON)
                .body("{\"totalRounds\": 10}")
                .when().post("/api/games")
                .then().statusCode(201)
                .body("status", equalTo("LOBBY"))
                .body("totalRounds", equalTo(10))
                .body("currentRound", equalTo(0))
                .body("insufficientQuestions", equalTo(false))
                .body("players", hasSize(0))
                .extract().asString();

        assertNotNull(io.restassured.path.json.JsonPath.from(body).getString("gameId"));
        assertFalse(body.contains("correctOption"), "el payload nunca debe incluir la respuesta correcta");
    }

    @Test
    void createGameRejectsInvalidOrMissingTotalRounds() {
        given().contentType(ContentType.JSON)
                .body("{\"totalRounds\": 7}")
                .when().post("/api/games")
                .then().statusCode(400)
                .body("message", containsString("inválida"));

        given().contentType(ContentType.JSON)
                .body("{}")
                .when().post("/api/games")
                .then().statusCode(400)
                .body("message", containsString("totalRounds"));
    }

    @Test
    void joinPlayerAddsThemToTheLobby() {
        String gameId = createGame(5);

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Ana\"}")
                .when().post("/api/games/{gameId}/players", gameId)
                .then().statusCode(201)
                .body("nickname", equalTo("Ana"))
                .body("score", equalTo(0))
                .body("connected", equalTo(true));

        // El playerId generado debe existir en el cuerpo
        String playerId = given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Beto\"}")
                .when().post("/api/games/{gameId}/players", gameId)
                .then().statusCode(201)
                .extract().jsonPath().getString("playerId");
        assertNotNull(playerId);

        // Vista pública de la partida con los dos jugadores
        given().when().get("/api/games/{gameId}", gameId)
                .then().statusCode(200)
                .body("status", equalTo("LOBBY"))
                .body("totalRounds", equalTo(5))
                .body("players.nickname", hasItems("Ana", "Beto"));
    }

    @Test
    void publicGamePayloadNeverExposesQuestionData() {
        String gameId = createGame(5);
        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Ana\"}")
                .when().post("/api/games/{gameId}/players", gameId)
                .then().statusCode(201);

        String body = given().when().get("/api/games/{gameId}", gameId)
                .then().statusCode(200)
                .extract().asString();

        assertFalse(body.contains("correctOption"));
        assertFalse(body.contains("currentQuestionId"));
        assertFalse(body.contains("optionA"));
        assertFalse(body.toLowerCase().contains("\"question\""));
    }

    @Test
    void joinRejectsDuplicateNicknameWith400() {
        String gameId = createGame(5);

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Ana\"}")
                .when().post("/api/games/{gameId}/players", gameId)
                .then().statusCode(201);

        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"ana\"}")
                .when().post("/api/games/{gameId}/players", gameId)
                .then().statusCode(400)
                .body("message", containsString("ya está en uso"));
    }

    @Test
    void joinOrReadUnknownGameReturns404() {
        given().contentType(ContentType.JSON)
                .body("{\"nickname\": \"Ana\"}")
                .when().post("/api/games/NOPE9/players")
                .then().statusCode(404)
                .body("message", containsString("No existe la partida"));

        given().when().get("/api/games/NOPE9")
                .then().statusCode(404)
                .body("message", containsString("No existe la partida"));
    }
}
