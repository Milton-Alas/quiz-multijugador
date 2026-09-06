package com.example.quiz.question;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests REST de la FASE 2: endpoint público de categorías.
 */
@QuarkusTest
class QuestionResourceTest {

    @Test
    void categoriesEndpointReturnsAllNineCategories() {
        List<String> categories = given()
                .when().get("/api/questions/categories")
                .then().statusCode(200)
                .extract().jsonPath().getList("$", String.class);

        assertEquals(9, categories.size());
        assertTrue(categories.containsAll(List.of(
                "FÚTBOL", "MATEMÁTICAS", "CIENCIA", "HISTORIA",
                "CULTURA GENERAL", "GEOGRAFÍA", "ANIMALES", "TECNOLOGÍA", "PELÍCULAS")));
    }

    @Test
    void categoriesEndpointNeverExposesAnswerData() {
        // El endpoint devuelve solo nombres de categoría: nada de preguntas
        // ni de la opción correcta.
        given()
                .when().get("/api/questions/categories")
                .then().statusCode(200)
                .body(not(org.hamcrest.Matchers.containsString("correctOption")))
                .body(not(org.hamcrest.Matchers.containsString("optionA")));
    }
}
