package com.example.quiz.question;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * API REST pública de preguntas.
 *
 * <p>Importante: ningún endpoint expone {@code correctOption} ni la entidad
 * {@link Question} completa. Solo se devuelven categorías (los DTOs de
 * preguntas llegarán en la FASE 5).
 */
@Path("/api/questions")
@Produces(MediaType.APPLICATION_JSON)
public class QuestionResource {

    private final QuestionRepository questionRepository;

    @Inject
    public QuestionResource(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    /** Devuelve las categorías con preguntas activas disponibles. */
    @GET
    @Path("/categories")
    public List<String> categories() {
        return questionRepository.findActiveCategories();
    }
}
