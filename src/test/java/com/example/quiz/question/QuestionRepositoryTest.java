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
 * con las migraciones Flyway aplicadas (V1 + V2 + V3).
 */
@QuarkusTest
class QuestionRepositoryTest {

    @Inject
    QuestionRepository questionRepository;

    @Test
    void migrationsLoadedTenQuestionsPerInitialCategory() {
        // V2 inserta una pregunta por categoría; V3 añade 9 más por categoría.
        assertEquals(80, questionRepository.count());
        assertEquals(80, questionRepository.countActive());
    }

    @Test
    void everyStoredQuestionIsCompleteAndValid() {
        List<Question> all = questionRepository.listAll();
        assertEquals(80, all.size());

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
    void noDuplicateQuestionTextsInTheBank() {
        List<String> texts = questionRepository.listAll().stream().map(q -> q.question).toList();
        assertEquals(80, new HashSet<>(texts).size(), "no debe haber textos de pregunta repetidos");
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

        // 8 categorías con 10 preguntas cada una
        assertEquals(8, all.size());
        assertEquals(80, all.values().stream().mapToInt(List::size).sum());
        all.values().forEach(ids -> assertEquals(10, ids.size(), "10 preguntas por categoría"));

        Long usedId = all.get("FÚTBOL").get(0);
        Map<String, List<Long>> withoutUsed =
                questionRepository.findAvailableQuestionIdsGroupedByCategory(Set.of(usedId));

        // Al excluir una pregunta de FÚTBOL quedan 9 en esa categoría (no desaparece)
        assertEquals(79, withoutUsed.values().stream().mapToInt(List::size).sum());
        assertEquals(9, withoutUsed.get("FÚTBOL").size(),
                "una categoría con más preguntas disponibles sigue en el mapa");
    }
}
