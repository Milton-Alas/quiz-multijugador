package com.example.quiz.web;

import com.example.quiz.game.GameNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Convierte {@link GameNotFoundException} en HTTP 404 con cuerpo JSON.
 * JAX-RS elige este mapeador por ser más específico que el de
 * IllegalArgumentException.
 */
@Provider
public class GameNotFoundExceptionMapper implements ExceptionMapper<GameNotFoundException> {

    @Override
    public Response toResponse(GameNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", exception.getMessage()))
                .build();
    }
}
