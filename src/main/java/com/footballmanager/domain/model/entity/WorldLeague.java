package com.footballmanager.domain.model.entity;

import java.util.UUID;

/**
 * WorldLeague - Liga en el WorldSnapshot.
 * Representa una liga real del sistema.
 */
public class WorldLeague {
    
    private UUID realLeagueId;      // ref a PostgreSQL leagues_table
    private String name;
    private String country;
    private Integer tier;           // 1 = primera división, 2 = segunda, etc.
    
    public WorldLeague() {
    }
    
    public WorldLeague(UUID realLeagueId, String name, String country, Integer tier) {
        this.realLeagueId = realLeagueId;
        this.name = name;
        this.country = country;
        this.tier = tier;
    }
    
    /**
     * Crea una WorldLeague desde una liga real
     */
    public static WorldLeague fromRealLeague(UUID leagueId, String name, String country, Integer tier) {
        return new WorldLeague(leagueId, name, country, tier);
    }
    
    // ========== Getters y Setters ==========
    
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

    public Integer getTier() {
        return tier;
    }

    public void setTier(Integer tier) {
        this.tier = tier;
    }
}
