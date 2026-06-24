package com.footballmanager.domain.model.valueobject;

/**
 * PlayerSkill - Catalogo de 10 habilidades que un jugador puede tener.
 *
 * V25D31: introduccion del modelo (solo metadata). V25D33-V25D34 agregan el uso
 * por parte del V24 engine.
 *
 * Categoria:
 *   - primarilyOffensive = true para HEADER, DRIBBLER, PLAYMAKER, SHOOTER.
 *   - primarilyDefensive = true para MARKER, TACKLER, WALL.
 *   - AERIAL, SPEEDSTER, PASSER son neutrales (no son ni offensive ni defensive primario).
 */
public enum PlayerSkill {

    HEADER("Header", "Cabeza / Salto en ataque", false, true),
    DRIBBLER("Dribbler", "Habilidad de regate y conduccion", false, true),
    PLAYMAKER("Playmaker", "Vision de juego y creacion de juego", false, true),
    AERIAL("Aerial", "Dominio del juego aereo en cualquier contexto", false, false),
    MARKER("Marker", "Marcaje al hombre en defensa", true, false),
    SPEEDSTER("Speedster", "Velocidad pura en cualquier contexto", false, false),
    TACKLER("Tackler", "Entradas y recuperacion de balon", true, false),
    SHOOTER("Shooter", "Habilidad de disparo / gol", false, true),
    PASSER("Passer", "Precision de pase general", false, false),
    WALL("Wall", "Muro defensivo / bloqueos en defensa", true, false);

    private final String displayName;
    private final String description;
    private final boolean defensive;
    private final boolean offensive;

    PlayerSkill(String displayName, String description, boolean defensive, boolean offensive) {
        this.displayName = displayName;
        this.description = description;
        this.defensive = defensive;
        this.offensive = offensive;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean primarilyDefensive() {
        return defensive;
    }

    public boolean primarilyOffensive() {
        return offensive;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
