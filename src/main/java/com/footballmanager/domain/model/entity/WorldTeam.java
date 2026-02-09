package com.footballmanager.domain.model.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WorldTeam - Equipo en el WorldSnapshot.
 * Puede ser REAL (desde PostgreSQL) o CUSTOM (creado por usuario).
 * 
 * NO es mutable durante el juego.
 * SessionTeam lo envuelve con estado mutable.
 */
public class WorldTeam {
    
    private String worldTeamId;          // ID único en WorldSnapshot
    private UUID realTeamId;             // ref a PostgreSQL teams_table (null si es custom)
    private UUID realLeagueId;           // liga a la que pertenece (null si es custom)
    private String name;
    private String country;
    private String city;
    private BigDecimal baseBudget;       // presupuesto base (inmutable)
    private String baseFormation;        // formación base (inmutable)
    private WorldTeamOrigin origin;      // REAL o CUSTOM
    
    public enum WorldTeamOrigin {
        REAL,     // Clonado de PostgreSQL
        CUSTOM    // Creado por usuario
    }
    
    public WorldTeam() {
    }
    
    /**
     * Crea un WorldTeam desde un equipo real de PostgreSQL
     */
    public static WorldTeam fromRealTeam(UUID realTeamId, UUID realLeagueId, String name,
                                         String country, String city, BigDecimal budget, String formation) {
        WorldTeam team = new WorldTeam();
        // worldTeamId = realTeamId para que WorldView.getTeamsByLeague() funcione correctamente
        team.worldTeamId = realTeamId.toString();
        team.realTeamId = realTeamId;
        team.realLeagueId = realLeagueId;
        team.name = name;
        team.country = country;
        team.city = city;
        team.baseBudget = budget;
        team.baseFormation = formation;
        team.origin = WorldTeamOrigin.REAL;
        return team;
    }
    
    /**
     * Crea un WorldTeam custom (creado por usuario)
     */
    public static WorldTeam createCustom(String name, String country, BigDecimal budget, String formation) {
        WorldTeam team = new WorldTeam();
        team.worldTeamId = UUID.randomUUID().toString();
        team.realTeamId = null;
        team.realLeagueId = null;
        team.name = name;
        team.country = country;
        team.city = null;
        team.baseBudget = budget;
        team.baseFormation = formation;
        team.origin = WorldTeamOrigin.CUSTOM;
        return team;
    }
    
    // ========== Getters y Setters ==========
    
    public String getWorldTeamId() {
        return worldTeamId;
    }

    public void setWorldTeamId(String worldTeamId) {
        this.worldTeamId = worldTeamId;
    }

    public UUID getRealTeamId() {
        return realTeamId;
    }

    public void setRealTeamId(UUID realTeamId) {
        this.realTeamId = realTeamId;
    }

    public UUID getRealLeagueId() {
        return realLeagueId;
    }

    public void setRealLeagueId(UUID realLeagueId) {
        this.realLeagueId = realLeagueId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public BigDecimal getBaseBudget() {
        return baseBudget;
    }

    public void setBaseBudget(BigDecimal baseBudget) {
        this.baseBudget = baseBudget;
    }

    public String getBaseFormation() {
        return baseFormation;
    }

    public void setBaseFormation(String baseFormation) {
        this.baseFormation = baseFormation;
    }

    public WorldTeamOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(WorldTeamOrigin origin) {
        this.origin = origin;
    }
}
