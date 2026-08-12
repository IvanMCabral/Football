package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.PlayerSkill;
import com.footballmanager.domain.model.valueobject.PlayerSpecialTrait;
import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Field-level owner deviations from a canonical real player. */
public final class WorldPlayerDelta {

    public enum Field {
        WORLD_TEAM_ID,
        NAME,
        AGE,
        POSITION,
        BASE_ATTACK,
        BASE_DEFENSE,
        BASE_TECHNIQUE,
        BASE_SPEED,
        BASE_STAMINA,
        BASE_MENTALITY,
        BASE_MARKET_VALUE,
        HEIGHT_CM,
        SKILL_LEVELS,
        SPECIAL_TRAITS
    }

    @WorldIdentityReference(domain = WorldIdentityDomain.REAL_PLAYER)
    private UUID realPlayerId;
    private Set<Field> changedFields = EnumSet.noneOf(Field.class);
    @WorldIdentityReference(domain = WorldIdentityDomain.WORLD_TEAM, nullable = true)
    private String worldTeamId;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String name;
    private Integer age;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String position;
    private Integer baseAttack;
    private Integer baseDefense;
    private Integer baseTechnique;
    private Integer baseSpeed;
    private Integer baseStamina;
    private Integer baseMentality;
    private BigDecimal baseMarketValue;
    private Integer heightCm;
    private Map<PlayerSkill, Integer> skillLevels;
    private List<PlayerSpecialTrait> specialTraits;

    public WorldPlayerDelta() {
    }

    public static WorldPlayerDelta between(WorldPlayer current, WorldPlayer canonical) {
        requireSameIdentity(current, canonical);
        WorldPlayerDelta delta = new WorldPlayerDelta();
        delta.realPlayerId = current.getRealPlayerId();
        delta.capture(Field.WORLD_TEAM_ID, current.getWorldTeamId(), canonical.getWorldTeamId(),
                value -> delta.worldTeamId = (String) value);
        delta.capture(Field.NAME, current.getName(), canonical.getName(), value -> delta.name = (String) value);
        delta.capture(Field.AGE, current.getAge(), canonical.getAge(), value -> delta.age = (Integer) value);
        delta.capture(Field.POSITION, current.getPosition(), canonical.getPosition(),
                value -> delta.position = (String) value);
        delta.capture(Field.BASE_ATTACK, current.getBaseAttack(), canonical.getBaseAttack(),
                value -> delta.baseAttack = (Integer) value);
        delta.capture(Field.BASE_DEFENSE, current.getBaseDefense(), canonical.getBaseDefense(),
                value -> delta.baseDefense = (Integer) value);
        delta.capture(Field.BASE_TECHNIQUE, current.getBaseTechnique(), canonical.getBaseTechnique(),
                value -> delta.baseTechnique = (Integer) value);
        delta.capture(Field.BASE_SPEED, current.getBaseSpeed(), canonical.getBaseSpeed(),
                value -> delta.baseSpeed = (Integer) value);
        delta.capture(Field.BASE_STAMINA, current.getBaseStamina(), canonical.getBaseStamina(),
                value -> delta.baseStamina = (Integer) value);
        delta.capture(Field.BASE_MENTALITY, current.getBaseMentality(), canonical.getBaseMentality(),
                value -> delta.baseMentality = (Integer) value);
        delta.capture(Field.BASE_MARKET_VALUE, current.getBaseMarketValue(), canonical.getBaseMarketValue(),
                value -> delta.baseMarketValue = (BigDecimal) value);
        delta.capture(Field.HEIGHT_CM, current.getHeightCm(), canonical.getHeightCm(),
                value -> delta.heightCm = (Integer) value);
        if (!Objects.equals(current.getSkillLevels(), canonical.getSkillLevels())) {
            delta.changedFields.add(Field.SKILL_LEVELS);
            delta.skillLevels = new LinkedHashMap<>(current.getSkillLevels());
        }
        if (!Objects.equals(current.getSpecialTraits(), canonical.getSpecialTraits())) {
            delta.changedFields.add(Field.SPECIAL_TRAITS);
            delta.specialTraits = new ArrayList<>(current.getSpecialTraits());
        }
        return delta.changedFields.isEmpty() ? null : delta;
    }

    public void applyTo(WorldPlayer target) {
        if (target == null || !Objects.equals(realPlayerId, target.getRealPlayerId())) {
            throw new IllegalStateException("World player delta identity mismatch");
        }
        if (changed(Field.WORLD_TEAM_ID)) target.setWorldTeamId(worldTeamId);
        if (changed(Field.NAME)) target.setName(name);
        if (changed(Field.AGE)) target.setAge(age);
        if (changed(Field.POSITION)) target.setPosition(position);
        if (changed(Field.BASE_ATTACK)) target.setBaseAttack(baseAttack);
        if (changed(Field.BASE_DEFENSE)) target.setBaseDefense(baseDefense);
        if (changed(Field.BASE_TECHNIQUE)) target.setBaseTechnique(baseTechnique);
        if (changed(Field.BASE_SPEED)) target.setBaseSpeed(baseSpeed);
        if (changed(Field.BASE_STAMINA)) target.setBaseStamina(baseStamina);
        if (changed(Field.BASE_MENTALITY)) target.setBaseMentality(baseMentality);
        if (changed(Field.BASE_MARKET_VALUE)) target.setBaseMarketValue(baseMarketValue);
        if (changed(Field.HEIGHT_CM)) target.setHeightCm(heightCm);
        if (changed(Field.SKILL_LEVELS)) target.setSkillLevels(skillLevels);
        if (changed(Field.SPECIAL_TRAITS)) target.setSpecialTraits(specialTraits);
    }

    private void capture(Field field, Object current, Object canonical,
                         java.util.function.Consumer<Object> setter) {
        if (!Objects.equals(current, canonical)) {
            changedFields.add(field);
            setter.accept(current);
        }
    }

    private boolean changed(Field field) {
        return changedFields != null && changedFields.contains(field);
    }

    private static void requireSameIdentity(WorldPlayer current, WorldPlayer canonical) {
        if (current == null || canonical == null || current.getRealPlayerId() == null
                || !Objects.equals(current.getRealPlayerId(), canonical.getRealPlayerId())) {
            throw new IllegalArgumentException("World player delta requires one canonical identity");
        }
    }

    public UUID getRealPlayerId() { return realPlayerId; }
    public void setRealPlayerId(UUID realPlayerId) { this.realPlayerId = realPlayerId; }
    public Set<Field> getChangedFields() { return changedFields; }
    public void setChangedFields(Set<Field> changedFields) {
        this.changedFields = changedFields == null || changedFields.isEmpty()
                ? EnumSet.noneOf(Field.class) : EnumSet.copyOf(changedFields);
    }
    public String getWorldTeamId() { return worldTeamId; }
    public void setWorldTeamId(String worldTeamId) { this.worldTeamId = worldTeamId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public Integer getBaseAttack() { return baseAttack; }
    public void setBaseAttack(Integer baseAttack) { this.baseAttack = baseAttack; }
    public Integer getBaseDefense() { return baseDefense; }
    public void setBaseDefense(Integer baseDefense) { this.baseDefense = baseDefense; }
    public Integer getBaseTechnique() { return baseTechnique; }
    public void setBaseTechnique(Integer baseTechnique) { this.baseTechnique = baseTechnique; }
    public Integer getBaseSpeed() { return baseSpeed; }
    public void setBaseSpeed(Integer baseSpeed) { this.baseSpeed = baseSpeed; }
    public Integer getBaseStamina() { return baseStamina; }
    public void setBaseStamina(Integer baseStamina) { this.baseStamina = baseStamina; }
    public Integer getBaseMentality() { return baseMentality; }
    public void setBaseMentality(Integer baseMentality) { this.baseMentality = baseMentality; }
    public BigDecimal getBaseMarketValue() { return baseMarketValue; }
    public void setBaseMarketValue(BigDecimal baseMarketValue) { this.baseMarketValue = baseMarketValue; }
    public Integer getHeightCm() { return heightCm; }
    public void setHeightCm(Integer heightCm) { this.heightCm = heightCm; }
    public Map<PlayerSkill, Integer> getSkillLevels() { return skillLevels; }
    public void setSkillLevels(Map<PlayerSkill, Integer> skillLevels) {
        this.skillLevels = skillLevels == null ? null : new LinkedHashMap<>(skillLevels);
    }
    public List<PlayerSpecialTrait> getSpecialTraits() { return specialTraits; }
    public void setSpecialTraits(List<PlayerSpecialTrait> specialTraits) {
        this.specialTraits = specialTraits == null ? null : new ArrayList<>(specialTraits);
    }
}
