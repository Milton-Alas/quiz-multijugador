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
 * con las migraciones Flyway aplicadas (V1 a V6).
 */
@QuarkusTest
class QuestionRepositoryTest {

    @Inject
    QuestionRepository questionRepository;

    @Test
    void migrationsLoadedExpectedQuestionCounts() {
        // 8 categorías originales: 1+9(EASY)+10(MEDIUM)+5(HARD)+10(V6)=35.
        // Nueva categoría PELÍCULAS (V6): 25 preguntas. Total: 8×35+25=305.
        assertEquals(305, questionRepository.count());
        assertEquals(305, questionRepository.countActive());
    }

    @Test
    void everyStoredQuestionIsCompleteAndValid() {
        List<Question> all = questionRepository.listAll();
        assertEquals(305, all.size());

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
        assertEquals(305, new HashSet<>(texts).size(), "no debe haber textos de pregunta repetidos");
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

        // 9 categorías: 8 con 35 preguntas y PELÍCULAS con 25
        assertEquals(9, all.size());
        assertEquals(305, all.values().stream().mapToInt(List::size).sum());
        all.forEach((category, ids) -> assertEquals(category.equals("PELÍCULAS") ? 25 : 35, ids.size(),
                category + " debe tener su cantidad esperada de preguntas"));

        Long usedId = all.get("FÚTBOL").get(0);
        Map<String, List<Long>> withoutUsed =
                questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of(usedId));

        // Al excluir una pregunta de FÚTBOL quedan 34 en esa categoría (no desaparece)
        assertEquals(304, withoutUsed.values().stream().mapToInt(List::size).sum());
        assertEquals(34, withoutUsed.get("FÚTBOL").size(),
                "una categoría con más preguntas disponibles sigue en el mapa");
    }
}
