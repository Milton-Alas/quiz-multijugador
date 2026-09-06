package com.example.quiz.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/**
 * Convierte errores de validación de dominio (IllegalArgumentException) en
 * HTTP 400 con un cuerpo JSON {"message": "..."}.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("message", exception.getMessage()))
                .build();
    }
}
