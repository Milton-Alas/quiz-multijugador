package com.example.quiz.game;

/**
 * Modalidad de juego de una partida.
 *
 * <p>{@code ONLINE}: cada jugador responde desde su propio dispositivo y
 * todos contestan la misma pregunta a la vez (modo original).
 *
 * <p>{@code LOCAL}: los jugadores comparten una sola pantalla; cada
 * pregunta la responde UN jugador por turnos (rotación en el orden de la
 * sala). El servidor anuncia de quién es el turno dentro de NEW_QUESTION
 * (campo {@code player}) y solo acepta la respuesta del jugador al que le
 * toca. Al responder, la ronda se cierra y se califica al instante.
 */
public enum GameMode {

    /** Cada quien en su dispositivo, todos a la vez. */
    ONLINE,

    /** Misma pantalla, por turnos. */
    LOCAL;

    /**
     * Parsea el modo de una petición; {@code null} o vacío = ONLINE.
     *
     * @throws IllegalArgumentException si el valor no es ONLINE ni LOCAL
     */
    public static GameMode parse(String mode) {
        if (mode == null || mode.isBlank()) {
            return ONLINE;
        }
        try {
            return valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Modo inválido: '" + mode + "' (valores permitidos: ONLINE, LOCAL)");
        }
    }
}
