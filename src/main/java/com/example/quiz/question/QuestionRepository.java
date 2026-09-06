package com.example.quiz.question;

import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.List;

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
}
