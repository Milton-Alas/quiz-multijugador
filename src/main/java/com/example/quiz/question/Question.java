package com.example.quiz.question;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidad persistida de una pregunta de quiz (solo preguntas viven en BD).
 *
 * <p>Seguridad: {@code correctOption} es un dato de servidor y jamás debe
 * serializarse en un DTO hacia el navegador (los recursos nunca devuelven
 * esta entidad directamente).
 */
@Entity
@Table(name = "questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** Categoría de la pregunta, p. ej. "FÚTBOL". */
    @Column(nullable = false, length = 100)
    public String category;

    /** Texto de la pregunta. */
    @Column(nullable = false, length = 1000)
    public String question;

    @Column(name = "option_a", nullable = false, length = 255)
    public String optionA;

    @Column(name = "option_b", nullable = false, length = 255)
    public String optionB;

    @Column(name = "option_c", nullable = false, length = 255)
    public String optionC;

    @Column(name = "option_d", nullable = false, length = 255)
    public String optionD;

    /** Letra de la opción correcta: "A", "B", "C" o "D". Solo del lado servidor. */
    @Column(name = "correct_option", nullable = false, length = 1)
    public String correctOption;

    /** Dificultad, p. ej. "EASY", "MEDIUM", "HARD". */
    @Column(nullable = false, length = 20)
    public String difficulty;

    /** Desactiva una pregunta sin borrarla (no entra en el juego). */
    @Column(nullable = false)
    public boolean active = true;

    /** Requerido por JPA. */
    protected Question() {
    }

    /** Construye una pregunta con todos sus datos (uso en tests y seeds). */
    public Question(String category, String question,
                    String optionA, String optionB, String optionC, String optionD,
                    String correctOption, String difficulty) {
        this.category = category;
        this.question = question;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctOption = correctOption;
        this.difficulty = difficulty;
        this.active = true;
    }
}
