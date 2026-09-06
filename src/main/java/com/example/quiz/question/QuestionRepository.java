package com.example.quiz.question;

import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Acceso a datos de {@link Question} (patrón repositorio sobre Panache).
 *
 * <p>Los métodos de consulta personalizados ejecutan dentro de una
 * transacción de solo lectura para garantizar un EntityManager válido.
 */
@ApplicationScoped
public class QuestionRepository implements PanacheRepository<Question> {

    /**
     * Categorías que tienen al menos una pregunta activa, ordenadas por nombre.
     */
    @Transactional
    public List<String> findActiveCategories() {
        return Panache.getEntityManager()
                .createQuery("select distinct q.category from Question q where q.active = true order by q.category",
                        String.class)
                .getResultList();
    }

    /** Número de preguntas activas. */
    public long countActive() {
        return count("active", true);
    }

    /** Carga una pregunta por su id. */
    @Transactional
    public Optional<Question> findActiveQuestionById(Long id) {
        return findByIdOptional(id);
    }

    /**
     * Devuelve los ids de las preguntas activas agrupadas por categoría,
     * excluyendo las preguntas ya usadas en una partida.
     *
     * <p>Las categorías sin preguntas disponibles (agotadas para esa partida)
     * simplemente no aparecen en el mapa: el {@code GameService} sortea solo
     * entre las que sí tienen opciones. Funciona igual con una pregunta por
     * categoría o con cientos: la bolsa se calcula con una consulta, no en
     * memoria del cliente.
     *
     * @param excludedQuestionIds ids de preguntas ya usadas (puede ser vacío)
     * @return categoría -> ids disponibles
     */
    @Transactional
    public Map<String, List<Long>> findAvailableQuestionIdsGroupedByCategory(Set<Long> excludedQuestionIds) {
        TypedQuery<Object[]> query;
        if (excludedQuestionIds == null || excludedQuestionIds.isEmpty()) {
            query = Panache.getEntityManager().createQuery(
                    "select q.category, q.id from Question q where q.active = true",
                    Object[].class);
        } else {
            query = Panache.getEntityManager().createQuery(
                    "select q.category, q.id from Question q"
                            + " where q.active = true and q.id not in :excluded",
                    Object[].class)
                    .setParameter("excluded", excludedQuestionIds);
        }
        Map<String, List<Long>> grouped = new LinkedHashMap<>();
        for (Object[] row : query.getResultList()) {
            grouped.computeIfAbsent((String) row[0], k -> new ArrayList<>()).add((Long) row[1]);
        }
        return grouped;
    }
}
