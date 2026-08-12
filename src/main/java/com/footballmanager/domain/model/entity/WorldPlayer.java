package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * WorldPlayer - Jugador en el WorldSnapshot.
 * Puede ser REAL (desde PostgreSQL) o CUSTOM (creado por usuario).
 *
 * NO es mutable durante el juego.
 * SessionPlayer lo envuelve con estado mutable (energy, form, injuries).
 *
 * LaLigaSeedService pueda persistir metadata fisica/skills a Postgres via el
 * entity layer. Los factories existentes quedan intactos (backward-compat) —
 * los nuevos campos se setean post-construccion via setters, o quedan null/empty
 * propagara estos campos al engine.
 */
public class WorldPlayer {

    /** Stable namespace for canonical world-player identities. */
    private static final UUID CANONICAL_ID_NAMESPACE =
            UUID.fromString("4a0f4e5d-8c65-4d51-9f4a-8c56a5b3c8d4");
    
    @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_PLAYER)
    private String worldPlayerId;        // ID único en WorldSnapshot
    @WorldIdentityReference(domain = WorldIdentityDomain.REAL_PLAYER, nullable = true)
    private UUID realPlayerId;           // ref a PostgreSQL players_table (null si es custom)
    @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_TEAM, nullable = true)
    private String worldTeamId;          // equipo al que pertenece en WorldSnapshot (puede ser null = free agent)
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
    private String name;
    private Integer age;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT)
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

    // provee; null/empty para players viejos (custom/random) que no tienen data
    private Integer heightCm;
    private Map<PlayerSkill, Integer> skillLevels;
    private List<PlayerSpecialTrait> specialTraits;

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

    /** Creates a canonical player with a stable catalog identity. */
    public static WorldPlayer fromCanonicalPlayer(UUID ownerId, UUID realPlayerId,
                                                   String worldTeamId, String name, Integer age,
                                                   String position, Integer attack, Integer defense,
                                                   Integer technique, Integer speed, Integer stamina,
                                                   Integer mentality, BigDecimal marketValue) {
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(realPlayerId, "realPlayerId cannot be null");
        WorldPlayer player = new WorldPlayer();
        player.worldPlayerId = stableCanonicalWorldPlayerId(ownerId, realPlayerId);
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
     * Exact UUIDv5-compatible identity contract used by canonical rebuilds.
     * The real-player UUID is the canonical catalog identity. The owner
     * argument remains part of the factory contract for call-site validation,
     * but is deliberately not encoded so one catalog can be shared safely.
     */
    public static String stableCanonicalWorldPlayerId(UUID ownerId, UUID realPlayerId) {
        Objects.requireNonNull(ownerId, "ownerId cannot be null");
        Objects.requireNonNull(realPlayerId, "realPlayerId cannot be null");
        String name = realPlayerId.toString().toLowerCase(java.util.Locale.ROOT);
        return UUID.nameUUIDFromBytes((CANONICAL_ID_NAMESPACE + ":" + name)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
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

    public Integer getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(Integer heightCm) {
        this.heightCm = heightCm;
    }

    /**
     * Read-only view of the skill levels map. Sparse: only contains skills with
     * level &gt; 0. Null/empty si el seeder no proveyo skills.
     */
    public Map<PlayerSkill, Integer> getSkillLevels() {
        return skillLevels == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(skillLevels);
    }

    public void setSkillLevels(Map<PlayerSkill, Integer> skillLevels) {
        this.skillLevels = skillLevels != null ? new HashMap<>(skillLevels) : null;
    }

    public List<PlayerSpecialTrait> getSpecialTraits() {
        return specialTraits == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(specialTraits);
    }

    public void setSpecialTraits(List<PlayerSpecialTrait> specialTraits) {
        this.specialTraits = specialTraits != null ? new ArrayList<>(specialTraits) : null;
    }
}
