package com.example.quiz.game;

import com.example.quiz.question.Question;
import com.example.quiz.question.QuestionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sustituto de {@link QuestionRepository} para tests unitarios (sin BD ni
 * Quarkus). Simula un catálogo de preguntas por categoría; cada id recibe
 * una {@link Question} de relleno cuya respuesta correcta es "A".
 */
class FakeQuestionRepository extends QuestionRepository {

    private final Map<Long, Question> questionsById = new LinkedHashMap<>();

    /** Añade preguntas (ids) a una categoría, con una Question de relleno. */
    void add(String category, Long... questionIds) {
        for (Long id : questionIds) {
            Question question = new Question(
                    category,
                    "Pregunta " + id + " de " + category,
                    "Opción A de " + id,
                    "Opción B de " + id,
                    "Opción C de " + id,
                    "Opción D de " + id,
                    "A", // respuesta correcta de las preguntas de relleno
                    "EASY");
            question.id = id;
            questionsById.put(id, question);
        }
    }

    /** Ids que tiene una categoría (para verificar pertenencia). */
    List<Long> idsOf(String category) {
        return questionsById.entrySet().stream()
                .filter(e -> e.getValue().category.equals(category))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public Optional<Question> findActiveQuestionById(Long id) {
        return Optional.ofNullable(questionsById.get(id));
    }

    @Override
    public Map<String, List<Long>> findAvailableQuestionIdsGroupedByCategory(Set<Long> excludedQuestionIds) {
        Set<Long> excluded = excludedQuestionIds == null ? Set.of() : excludedQuestionIds;
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        questionsById.forEach((id, question) -> {
            if (!excluded.contains(id)) {
                grouped.computeIfAbsent(question.category, k -> new ArrayList<>()).add(id);
            }
        });
        return grouped;
    }
}
