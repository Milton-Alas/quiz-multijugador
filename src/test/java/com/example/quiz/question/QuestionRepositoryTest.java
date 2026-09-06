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
 * con las migraciones Flyway aplicadas (V1 a V4).
 */
@QuarkusTest
class QuestionRepositoryTest {

    @Inject
    QuestionRepository questionRepository;

    @Test
    void migrationsLoadedTwentyFiveQuestionsPerInitialCategory() {
        // V2: 1. V3: +9 (EASY). V4: +10 (MEDIUM). V5: +5 (HARD) -> 25 por categoría.
        assertEquals(200, questionRepository.count());
        assertEquals(200, questionRepository.countActive());
    }

    @Test
    void everyStoredQuestionIsCompleteAndValid() {
        List<Question> all = questionRepository.listAll();
        assertEquals(200, all.size());

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
        assertEquals(200, new HashSet<>(texts).size(), "no debe haber textos de pregunta repetidos");
    }

    @Test
    void categoriesMatchTheEightInitialCategories() {
        List<String> expected = List.of(
                "ANIMALES", "CIENCIA", "CULTURA GENERAL", "FÚTBOL",
                "GEOGRAFÍA", "HISTORIA", "MATEMÁTICAS", "TECNOLOGÍA");

        List<String> categories = questionRepository.findActiveCategories();

        assertEquals(expected.size(), categories.size());
        assertTrue(categories.containsAll(expected), "deben existir las 8 categorías iniciales: " + categories);
    }

    @Test
    void availableIdsGroupedByCategoryExcludesUsedQuestions() {
        Map<String, List<Long>> all = questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of());

        // 8 categorías con 25 preguntas cada una
        assertEquals(8, all.size());
        assertEquals(200, all.values().stream().mapToInt(List::size).sum());
        all.values().forEach(ids -> assertEquals(25, ids.size(), "25 preguntas por categoría"));

        Long usedId = all.get("FÚTBOL").get(0);
        Map<String, List<Long>> withoutUsed =
                questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of(usedId));

        // Al excluir una pregunta de FÚTBOL quedan 24 en esa categoría (no desaparece)
        assertEquals(199, withoutUsed.values().stream().mapToInt(List::size).sum());
        assertEquals(24, withoutUsed.get("FÚTBOL").size(),
                "una categoría con más preguntas disponibles sigue en el mapa");
    }
}
