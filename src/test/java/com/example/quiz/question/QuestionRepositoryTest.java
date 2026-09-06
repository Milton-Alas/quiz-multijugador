package com.example.quiz.question;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la FASE 2/4 sobre el repositorio Panache, usando H2 en memoria
 * con las migraciones Flyway aplicadas (V1 a V7).
 */
@QuarkusTest
class QuestionRepositoryTest {

    @Inject
    QuestionRepository questionRepository;

    @Test
    void migrationsLoadedExpectedQuestionCounts() {
        // V7 añade 196 preguntas (106 aportadas + 90 MEDIUM) -> 305 + 196 = 501.
        assertEquals(501, questionRepository.count());
        assertEquals(501, questionRepository.countActive());
    }

    @Test
    void everyStoredQuestionIsCompleteAndValid() {
        List<Question> all = questionRepository.listAll();
        assertEquals(501, all.size());

        for (Question q : all) {
            assertNotNull(q.category, "la categoría no debe ser nula");
            assertNotNull(q.question, "el texto no debe ser nulo");
            assertNotNull(q.optionA);
            assertNotNull(q.optionB);
            assertNotNull(q.optionC);
            assertNotNull(q.optionD);
            // correctOption es un dato interno del servidor (A-D)
            assertTrue(q.correctOption.matches("[A-D]"), "correct_option debe ser A-D");
            assertTrue(q.difficulty.equals("EASY") || q.difficulty.equals("MEDIUM")
                            || q.difficulty.equals("HARD"),
                    "dificultad debe ser EASY, MEDIUM o HARD: " + q.difficulty);
            assertTrue(q.active);
        }
    }

    @Test
    void noDuplicateQuestionTextsInTheBank() {
        List<String> texts = questionRepository.listAll().stream().map(q -> q.question).toList();
        assertEquals(501, new HashSet<>(texts).size(), "no debe haber textos de pregunta repetidos");
    }

    @Test
    void categoriesMatchAllNineCategories() {
        List<String> expected = List.of(
                "ANIMALES", "CIENCIA", "CULTURA GENERAL", "FÚTBOL",
                "GEOGRAFÍA", "HISTORIA", "MATEMÁTICAS", "PELÍCULAS", "TECNOLOGÍA");

        List<String> categories = questionRepository.findActiveCategories();

        assertEquals(expected.size(), categories.size());
        assertTrue(categories.containsAll(expected), "deben existir las 9 categorías: " + categories);
    }

    @Test
    void availableIdsGroupedByCategoryExcludesUsedQuestions() {
        Map<String, List<Long>> all = questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of());

        // 9 categorías con sus tamaños tras V6 + V7
        Map<String, Integer> expectedSizes = Map.of(
                "ANIMALES", 54, "CIENCIA", 54, "CULTURA GENERAL", 54, "FÚTBOL", 55,
                "GEOGRAFÍA", 52, "HISTORIA", 54, "MATEMÁTICAS", 53, "PELÍCULAS", 70,
                "TECNOLOGÍA", 55);
        assertEquals(9, all.size());
        assertEquals(501, all.values().stream().mapToInt(List::size).sum());
        all.forEach((category, ids) -> assertEquals(expectedSizes.get(category), ids.size(),
                category + " debe tener su cantidad esperada de preguntas"));

        Long usedId = all.get("FÚTBOL").get(0);
        Map<String, List<Long>> withoutUsed =
                questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of(usedId));

        // Al excluir una pregunta de FÚTBOL quedan 54 en esa categoría (no desaparece)
        assertEquals(500, withoutUsed.values().stream().mapToInt(List::size).sum());
        assertEquals(54, withoutUsed.get("FÚTBOL").size(),
                "una categoría con más preguntas disponibles sigue en el mapa");
    }
}
