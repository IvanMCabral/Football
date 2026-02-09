package com.footballmanager.domain.model.entity;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * WorldPlayer - Jugador en el WorldSnapshot.
 * Puede ser REAL (desde PostgreSQL) o CUSTOM (creado por usuario).
 * 
 * NO es mutable durante el juego.
 * SessionPlayer lo envuelve con estado mutable (energy, form, injuries).
 */
public class WorldPlayer {
    
    private String worldPlayerId;        // ID único en WorldSnapshot
    private UUID realPlayerId;           // ref a PostgreSQL players_table (null si es custom)
    private String worldTeamId;          // equipo al que pertenece en WorldSnapshot (puede ser null = free agent)
    private String name;
    private Integer age;
    private String position;             // GK, DEF, MID, WINGER, ATT
    
    // Atributos base (inmutables)
    private Integer baseAttack;
    private Integer baseDefense;
    private Integer baseTechnique;
    private Integer baseSpeed;
    private Integer baseStamina;
    private Integer baseMentality;
    private BigDecimal baseMarketValue;
    
    private WorldPlayerOrigin origin;    // REAL, CUSTOM o RANDOM
    
    public enum WorldPlayerOrigin {
        REAL,     // Clonado de PostgreSQL
        CUSTOM,   // Creado por usuario manualmente
        RANDOM    // Generado aleatoriamente
    }
    
    public WorldPlayer() {
    }
    
    /**
     * Crea un WorldPlayer desde un jugador real de PostgreSQL
     */
    public static WorldPlayer fromRealPlayer(UUID realPlayerId, String worldTeamId, String name, Integer age,
                                            String position, Integer attack, Integer defense, Integer technique,
                                            Integer speed, Integer stamina, Integer mentality, BigDecimal marketValue) {
        WorldPlayer player = new WorldPlayer();
        player.worldPlayerId = UUID.randomUUID().toString();
        player.realPlayerId = realPlayerId;
        player.worldTeamId = worldTeamId;
        player.name = name;
        player.age = age;
        player.position = position;
        player.baseAttack = attack;
        player.baseDefense = defense;
        player.baseTechnique = technique;
        player.baseSpeed = speed;
        player.baseStamina = stamina;
        player.baseMentality = mentality;
        player.baseMarketValue = marketValue;
        player.origin = WorldPlayerOrigin.REAL;
        return player;
    }
    
    /**
     * Crea un WorldPlayer custom (creado por usuario)
     */
    public static WorldPlayer createCustom(String name, Integer age, String position,
                                          Integer attack, Integer defense, Integer technique,
                                          Integer speed, Integer stamina, Integer mentality,
                                          BigDecimal marketValue) {
        WorldPlayer player = new WorldPlayer();
        player.worldPlayerId = UUID.randomUUID().toString();
        player.realPlayerId = null;
        player.worldTeamId = null;  // free agent
        player.name = name;
        player.age = age;
        player.position = position;
        player.baseAttack = attack;
        player.baseDefense = defense;
        player.baseTechnique = technique;
        player.baseSpeed = speed;
        player.baseStamina = stamina;
        player.baseMentality = mentality;
        player.baseMarketValue = marketValue;
        player.origin = WorldPlayerOrigin.CUSTOM;
        return player;
    }
    
    /**
     * Crea un WorldPlayer random (generado aleatoriamente)
     */
    public static WorldPlayer createRandom(String name, Integer age, String position,
                                          Integer attack, Integer defense, Integer technique,
                                          Integer speed, Integer stamina, Integer mentality,
                                          BigDecimal marketValue) {
        WorldPlayer player = new WorldPlayer();
        player.worldPlayerId = UUID.randomUUID().toString();
        player.realPlayerId = null;
        player.worldTeamId = null;  // free agent
        player.name = name;
        player.age = age;
        player.position = position;
        player.baseAttack = attack;
        player.baseDefense = defense;
        player.baseTechnique = technique;
        player.baseSpeed = speed;
        player.baseStamina = stamina;
        player.baseMentality = mentality;
        player.baseMarketValue = marketValue;
        player.origin = WorldPlayerOrigin.RANDOM;
        return player;
    }
    
    /**
     * Calcula el overall base del jugador según su posición
     */
    public Integer calculateOverall() {
        if (baseAttack == null || baseDefense == null || baseTechnique == null || 
            baseSpeed == null || baseStamina == null || baseMentality == null) {
            return 50;
        }
        
        double overall = switch (position) {
            case "GK" -> 
                baseDefense * 0.40 + 
                baseTechnique * 0.20 + 
                baseMentality * 0.20 + 
                baseStamina * 0.10 + 
                baseSpeed * 0.05 + 
                baseAttack * 0.05;
            
            case "DEF" -> 
                baseDefense * 0.35 + 
                baseTechnique * 0.15 + 
                baseMentality * 0.15 + 
                baseStamina * 0.15 + 
                baseSpeed * 0.10 + 
                baseAttack * 0.10;
            
            case "MID" -> 
                baseTechnique * 0.30 + 
                baseStamina * 0.20 + 
                baseMentality * 0.15 + 
                baseDefense * 0.15 + 
                baseSpeed * 0.10 + 
                baseAttack * 0.10;
            
            case "WINGER" -> 
                baseSpeed * 0.30 + 
                baseAttack * 0.25 + 
                baseTechnique * 0.20 + 
                baseStamina * 0.15 + 
                baseMentality * 0.05 + 
                baseDefense * 0.05;
            
            case "ATT" -> 
                baseAttack * 0.40 + 
                baseTechnique * 0.20 + 
                baseSpeed * 0.15 + 
                baseMentality * 0.10 + 
                baseStamina * 0.10 + 
                baseDefense * 0.05;
            
            default -> (baseAttack + baseDefense + baseTechnique + baseSpeed + baseStamina + baseMentality) / 6.0;
        };
        
        return (int) Math.round(overall);
    }
    
    // ========== Getters y Setters ==========
    
    public String getWorldPlayerId() {
        return worldPlayerId;
    }

    public void setWorldPlayerId(String worldPlayerId) {
        this.worldPlayerId = worldPlayerId;
    }

    public UUID getRealPlayerId() {
        return realPlayerId;
    }

    public void setRealPlayerId(UUID realPlayerId) {
        this.realPlayerId = realPlayerId;
    }

    public String getWorldTeamId() {
        return worldTeamId;
    }

    public void setWorldTeamId(String worldTeamId) {
        this.worldTeamId = worldTeamId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public Integer getBaseAttack() {
        return baseAttack;
    }

    public void setBaseAttack(Integer baseAttack) {
        this.baseAttack = baseAttack;
    }

    public Integer getBaseDefense() {
        return baseDefense;
    }

    public void setBaseDefense(Integer baseDefense) {
        this.baseDefense = baseDefense;
    }

    public Integer getBaseTechnique() {
        return baseTechnique;
    }

    public void setBaseTechnique(Integer baseTechnique) {
        this.baseTechnique = baseTechnique;
    }

    public Integer getBaseSpeed() {
        return baseSpeed;
    }

    public void setBaseSpeed(Integer baseSpeed) {
        this.baseSpeed = baseSpeed;
    }

    public Integer getBaseStamina() {
        return baseStamina;
    }

    public void setBaseStamina(Integer baseStamina) {
        this.baseStamina = baseStamina;
    }

    public Integer getBaseMentality() {
        return baseMentality;
    }

    public void setBaseMentality(Integer baseMentality) {
        this.baseMentality = baseMentality;
    }

    public BigDecimal getBaseMarketValue() {
        return baseMarketValue;
    }

    public void setBaseMarketValue(BigDecimal baseMarketValue) {
        this.baseMarketValue = baseMarketValue;
    }

    public WorldPlayerOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(WorldPlayerOrigin origin) {
        this.origin = origin;
    }
}
