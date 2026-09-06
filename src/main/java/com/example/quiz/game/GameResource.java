package com.example.quiz.game;

import com.example.quiz.game.dto.CreateGameRequest;
import com.example.quiz.game.dto.GameDto;
import com.example.quiz.game.dto.JoinGameRequest;
import com.example.quiz.game.dto.PlayerDto;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * API REST pública de partidas (estado en memoria).
 *
 * <p>Los DTOs de salida nunca exponen la pregunta actual ni la opción
 * correcta: el navegador solo recibe {@link GameDto} y {@link PlayerDto}.
 */
@Path("/api/games")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GameResource {

    private final GameService gameService;

    @Inject
    public GameResource(GameService gameService) {
        this.gameService = gameService;
    }

    /** Crea una partida. Cuerpo: {"totalRounds": 5|10|15, "mode"?: "ONLINE"|"LOCAL"}. */
    @POST
    public Response createGame(CreateGameRequest request) {
        if (request == null || request.totalRounds() == null) {
            throw new IllegalArgumentException(
                    "La cantidad de preguntas (totalRounds) es obligatoria");
        }
        GameSession session = gameService.createGame(request.totalRounds(), request.mode());
        return Response.status(Response.Status.CREATED).entity(toGameDto(session)).build();
    }

    /** Añade un jugador a la partida. Cuerpo: {"nickname": "Ana"}. */
    @POST
    @Path("/{gameId}/players")
    public Response addPlayer(@PathParam("gameId") String gameId, JoinGameRequest request) {
        Player player = gameService.joinGame(gameId, request == null ? null : request.nickname());
        return Response.status(Response.Status.CREATED).entity(toPlayerDto(player)).build();
    }

    /** Información pública de la partida (para la sala de espera y el juego). */
    @GET
    @Path("/{gameId}")
    public GameDto getGame(@PathParam("gameId") String gameId) {
        GameSession session = gameService.findGame(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        return toGameDto(session);
    }

    private GameDto toGameDto(GameSession session) {
        List<PlayerDto> players = session.players.stream().map(this::toPlayerDto).toList();
        return new GameDto(session.gameId, session.status, session.mode,
                session.totalRounds, session.currentRound, session.insufficientQuestions, players);
    }

    private PlayerDto toPlayerDto(Player player) {
        return new PlayerDto(player.playerId, player.nickname, player.score, player.connected);
    }
}
