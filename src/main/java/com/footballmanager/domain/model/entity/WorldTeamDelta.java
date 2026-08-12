package com.footballmanager.domain.model.entity;

import com.footballmanager.domain.model.valueobject.Division;
import com.footballmanager.domain.model.metadata.WorldIdentityDomain;
import com.footballmanager.domain.model.metadata.WorldIdentityReference;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Field-level owner deviations from a canonical real team. */
public final class WorldTeamDelta {

    public enum Field {
        REAL_LEAGUE_ID,
        NAME,
        COUNTRY,
        CITY,
        BASE_BUDGET,
        BASE_FORMATION,
        DIVISION
    }

    @WorldIdentityReference(domain = WorldIdentityDomain.REAL_TEAM)
    private UUID realTeamId;
    private Set<Field> changedFields = EnumSet.noneOf(Field.class);
    @WorldIdentityReference(domain = WorldIdentityDomain.OTHER_ID, nullable = true)
    private UUID realLeagueId;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String name;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String country;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String city;
    private BigDecimal baseBudget;
    @WorldIdentityReference(domain = WorldIdentityDomain.NON_ID_TEXT, nullable = true)
    private String baseFormation;
    private Division division;

    public WorldTeamDelta() {
    }

    public static WorldTeamDelta between(WorldTeam current, WorldTeam canonical) {
        requireSameIdentity(current, canonical);
        WorldTeamDelta delta = new WorldTeamDelta();
        delta.realTeamId = current.getRealTeamId();
        delta.capture(Field.REAL_LEAGUE_ID, current.getRealLeagueId(), canonical.getRealLeagueId(),
                value -> delta.realLeagueId = (UUID) value);
        delta.capture(Field.NAME, current.getName(), canonical.getName(), value -> delta.name = (String) value);
        delta.capture(Field.COUNTRY, current.getCountry(), canonical.getCountry(),
                value -> delta.country = (String) value);
        delta.capture(Field.CITY, current.getCity(), canonical.getCity(), value -> delta.city = (String) value);
        delta.capture(Field.BASE_BUDGET, current.getBaseBudget(), canonical.getBaseBudget(),
                value -> delta.baseBudget = (BigDecimal) value);
        delta.capture(Field.BASE_FORMATION, current.getBaseFormation(), canonical.getBaseFormation(),
                value -> delta.baseFormation = (String) value);
        delta.capture(Field.DIVISION, current.getDivision(), canonical.getDivision(),
                value -> delta.division = (Division) value);
        return delta.changedFields.isEmpty() ? null : delta;
    }

    public void applyTo(WorldTeam target) {
        if (target == null || !Objects.equals(realTeamId, target.getRealTeamId())) {
            throw new IllegalStateException("World team delta identity mismatch");
        }
        if (changed(Field.REAL_LEAGUE_ID)) target.setRealLeagueId(realLeagueId);
        if (changed(Field.NAME)) target.setName(name);
        if (changed(Field.COUNTRY)) target.setCountry(country);
        if (changed(Field.CITY)) target.setCity(city);
        if (changed(Field.BASE_BUDGET)) target.setBaseBudget(baseBudget);
        if (changed(Field.BASE_FORMATION)) target.setBaseFormation(baseFormation);
        if (changed(Field.DIVISION)) target.setDivision(division);
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

    private static void requireSameIdentity(WorldTeam current, WorldTeam canonical) {
        if (current == null || canonical == null || current.getRealTeamId() == null
                || !Objects.equals(current.getRealTeamId(), canonical.getRealTeamId())) {
            throw new IllegalArgumentException("World team delta requires one canonical identity");
        }
    }

    public UUID getRealTeamId() { return realTeamId; }
    public void setRealTeamId(UUID realTeamId) { this.realTeamId = realTeamId; }
    public Set<Field> getChangedFields() { return changedFields; }
    public void setChangedFields(Set<Field> changedFields) {
        this.changedFields = changedFields == null || changedFields.isEmpty()
                ? EnumSet.noneOf(Field.class) : EnumSet.copyOf(changedFields);
    }
    public UUID getRealLeagueId() { return realLeagueId; }
    public void setRealLeagueId(UUID realLeagueId) { this.realLeagueId = realLeagueId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public BigDecimal getBaseBudget() { return baseBudget; }
    public void setBaseBudget(BigDecimal baseBudget) { this.baseBudget = baseBudget; }
    public String getBaseFormation() { return baseFormation; }
    public void setBaseFormation(String baseFormation) { this.baseFormation = baseFormation; }
    public Division getDivision() { return division; }
    public void setDivision(Division division) { this.division = division; }
}
