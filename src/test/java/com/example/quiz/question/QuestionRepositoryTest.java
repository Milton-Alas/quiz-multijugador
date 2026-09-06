package com.example.quiz.question;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la FASE 2/4 sobre el repositorio Panache, usando H2 en memoria
 * con las migraciones Flyway aplicadas (V1 + V2).
 */
@QuarkusTest
class QuestionRepositoryTest {

    @Inject
    QuestionRepository questionRepository;

    @Test
    void migrationsLoadedOneQuestionPerInitialCategory() {
        // V2 inserta exactamente una pregunta por cada categoría inicial
        assertEquals(8, questionRepository.count());
        assertEquals(8, questionRepository.countActive());
    }

    @Test
    void everyStoredQuestionIsCompleteAndValid() {
        List<Question> all = questionRepository.listAll();
        assertEquals(8, all.size());

        for (Question q : all) {
            assertNotNull(q.category, "la categoría no debe ser nula");
            assertNotNull(q.question, "el texto no debe ser nulo");
            assertNotNull(q.optionA);
            assertNotNull(q.optionB);
            assertNotNull(q.optionC);
            assertNotNull(q.optionD);
            // correctOption es un dato interno del servidor (A-D)
            assertTrue(q.correctOption.matches("[A-D]"), "correct_option debe ser A-D");
            assertEquals("EASY", q.difficulty);
            assertTrue(q.active);
        }
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

        // 8 categorías, 1 pregunta cada una
        assertEquals(8, all.size());
        assertEquals(8, all.values().stream().mapToInt(List::size).sum());

        Long usedId = all.get("FÚTBOL").get(0);
        Map<String, List<Long>> withoutUsed =
                questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of(usedId));

        // Al excluir la única pregunta de FÚTBOL, esa categoría desaparece
        assertEquals(7, withoutUsed.values().stream().mapToInt(List::size).sum());
        assertFalse(withoutUsed.containsKey("FÚTBOL"),
                "una categoría agotada debe quedar fuera del mapa de disponibles");
    }
}
