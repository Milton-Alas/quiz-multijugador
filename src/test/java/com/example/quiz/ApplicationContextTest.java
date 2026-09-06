package com.example.quiz;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de humo de la FASE 1: verifica que el contexto de Quarkus arranca
 * correctamente con Java 21 y que la infraestructura de tests funciona.
 * Se reemplazará por tests reales en fases posteriores.
 */
@QuarkusTest
class ApplicationContextTest {

    @Test
    void quarkusContextStarts() {
        assertTrue(true, "El contexto de Quarkus debe arrancar sin errores");
    }
}
