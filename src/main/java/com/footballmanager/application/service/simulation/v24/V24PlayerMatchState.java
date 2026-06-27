package com.footballmanager.application.service.simulation.v24;

import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.valueobject.PlayerSkill;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable per-player match state, copied from SessionPlayer at match start.
 * SessionPlayer itself is NOT mutated.
 *
 * <p>V25D32-F4: agregados {@code heightCm} y {@code skillLevels} como plumbing
 * para que V25D33 pueda consumirlos en el engine. V25D32 NO usa estos fields —
 * se copian del SessionPlayer para que esten disponibles cuando el engine
 * (V25D33-V25D34) los lea via {@code V24ShotXgCalculator} overload 9-args.
 */
public class V24PlayerMatchState {

    private final String sessionPlayerId;
    private String teamId;
    private final String name;
    // LIVE-MATCH-F2-LIVE F5 (B2): 'position' is NO LONGER final. A tactical
    // formation change can move a player to a different slot; the
    // TacticalChangeService drives the position reassignment through this
    // setter (validation: NOT NULL — compatibility with the new formation
    // is the service's responsibility, not the player's).
    //
    // V25D47 (Sprint C11a): the existing 'position' field is now the
    // TACTICAL position (current slot category, e.g. "MID" if a CB
    // got moved to a MID slot). The new 'naturalPosition' field holds the
    // player's original position (immutable, set at fromSessionPlayer
    // time). Both are 5-category strings ("GK"/"DEF"/"MID"/"WINGER"/"ATT").
    // The engine uses PositionEffectivenessCalculator.effectiveness(
    // naturalPosition, position) to weight stat contributions — a CB at
    // a MID slot contributes attack_stat * 0.8 instead of the full value.
    private String position;
    private final String naturalPosition;
    private final int attack;
    private final int defense;
    private final int technique;
    private final int speed;
    private final int stamina;
    private final int mentality;
    private int currentStamina;
    private final int form;
    private int yellowCards;
    private boolean redCard;
    private boolean injured;
    private boolean onPitch;

    // V25D32-F4: height + skill metadata. Copiados del SessionPlayer (sparse map,
    // unmodifiable view). Engine en V25D33 leera esto via el overload 9-args de
    // V24ShotXgCalculator.calculateXg(...). Por ahora el engine NO los usa.
    private final Integer heightCm;
    private final Map<PlayerSkill, Integer> skillLevels;

    private V24PlayerMatchState(
            String sessionPlayerId, String teamId, String name, String position,
            String naturalPosition,
            int attack, int defense, int technique, int speed, int stamina, int mentality,
            int currentStamina, int form, int yellowCards, boolean redCard,
            boolean injured, boolean onPitch,
            Integer heightCm, Map<PlayerSkill, Integer> skillLevels) {
        this.sessionPlayerId = sessionPlayerId;
        this.teamId = teamId;
        this.name = name;
        this.position = position;
        // V25D47 (Sprint C11a): naturalPosition = the player's original slot.
        // If null/blank, fall back to the tactical position so effectiveness()
        // returns 1.0 (perfect match) — the player is treated as being at
        // home. This is the same backward-compat shape as legacy lineups.
        this.naturalPosition = (naturalPosition == null || naturalPosition.isBlank())
                ? position
                : naturalPosition;
        this.attack = attack;
        this.defense = defense;
        this.technique = technique;
        this.speed = speed;
        this.stamina = stamina;
        this.mentality = mentality;
        this.currentStamina = currentStamina;
        this.form = form;
        this.yellowCards = yellowCards;
        this.redCard = redCard;
        this.injured = injured;
        this.onPitch = onPitch;
        this.heightCm = heightCm;
        // Defensive copy: el SessionPlayer tiene unmodifiable view, pero copiamos
        // para que mutaciones del caller (que son null) no afecten nuestro state.
        this.skillLevels = (skillLevels == null || skillLevels.isEmpty())
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new HashMap<>(skillLevels));
    }

    public static V24PlayerMatchState fromSessionPlayer(SessionPlayer player, String teamId) {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(teamId, "teamId must not be null");
        String name = (player.getName() == null || player.getName().isBlank())
                ? "Unknown Player" : player.getName();
        // V25D47 (Sprint C11a): naturalPosition == position at construction
        // (no tactical change has happened yet). When the TacticalChangeService
        // reassigns a player mid-match, it calls setPosition() and the
        // effectiveness calc kicks in.
        String playerPos = player.getPosition();
        return new V24PlayerMatchState(
                player.getSessionPlayerId(),
                teamId,
                name,
                playerPos,
                playerPos,
                intOr(player.getAttack(), 50),
                intOr(player.getDefense(), 50),
                intOr(player.getTechnique(), 50),
                intOr(player.getSpeed(), 50),
                intOr(player.getStamina(), 50),
                intOr(player.getMentality(), 50),
                intOr(player.getEnergy(), 100),
                intOr(player.getForm(), 50),
                0, false,
                player.getInjured() != null && player.getInjured(),
                true,  // onPitch initially
                player.getHeightCm(),         // V25D32-F4
                player.getSkillLevels()       // V25D32-F4 (already unmodifiable)
        );
    }

    private static int intOr(Integer v, int fallback) {
        return v != null ? v : fallback;
    }

    // Getters
    public String sessionPlayerId() { return sessionPlayerId; }
    public String teamId() { return teamId; }
    public String name() { return name; }
    public String position() { return position; }

    /**
     * V25D47 (Sprint C11a): the player's original / natural category
     * (e.g., {@code "DEF"} for a CB). Immutable — set at construction
     * from {@link SessionPlayer#getPosition()} and never changes. Compare
     * with {@link #position()} (current tactical slot, mutable) to compute
     * the effectiveness multiplier via
     * {@link com.footballmanager.domain.model.valueobject.PositionEffectivenessCalculator}.
     */
    public String naturalPosition() { return naturalPosition; }
    public int attack() { return attack; }
    public int defense() { return defense; }
    public int technique() { return technique; }
    public int speed() { return speed; }
    public int stamina() { return stamina; }
    public int mentality() { return mentality; }
    public int currentStamina() { return currentStamina; }
    public int form() { return form; }
    public int yellowCards() { return yellowCards; }
    public boolean redCard() { return redCard; }
    public boolean injured() { return injured; }
    public boolean onPitch() { return onPitch; }

    // V25D32-F4: height + skill accessors (read-only).
    public Integer heightCm() { return heightCm; }
    public Map<PlayerSkill, Integer> skillLevels() { return skillLevels; }

    /**
     * V25D33-F2: convenience accessor for a single skill level. Mirrors
     * {@link SessionPlayer#getSkillLevel(PlayerSkill)} for the same null-safe
     * semantics — returns 0 when the skill is absent, the map is null, or the
     * skill is not present in the sparse map. Used by the engine to read
     * DRIBBLER (F2), WALL (F3), and other Tier-1 skills without scattering
     * null-checks across the per-minute loop.
     */
    public int getSkillLevel(PlayerSkill skill) {
        if (skill == null || skillLevels == null || skillLevels.isEmpty()) return 0;
        Integer level = skillLevels.get(skill);
        return level == null ? 0 : level;
    }

    // Setters (for match simulation mutability)
    public void setTeamId(String teamId) { this.teamId = teamId; }

    // ========== LIVE-MATCH-F2-LIVE F5 (B2): tactical position reassignment ==========

    /**
     * LIVE-MATCH-F2-LIVE F5 (B2): replace the player's on-pitch position slot.
     * Validates non-null. The setter does NOT validate compatibility with the
     * current formation — that is the caller's responsibility
     * (TacticalChangeService picks a slot that fits the new formation).
     *
     * <p>Used only when a manager changes formation mid-match and a player
     * must be reassigned to a different slot. For all other mutations
     * (drain stamina, yellow card, etc.) the existing setters remain in use.
     *
     * @param position new position (NOT NULL, e.g. "GK", "DEF", "MID", "ATT", "WINGER")
     * @throws IllegalArgumentException if position is null
     */
    public void setPosition(String position) {
        if (position == null || position.isBlank()) {
            throw new IllegalArgumentException("position must not be null or blank");
        }
        this.position = position;
    }

    public void drainStamina(int amount) {
        currentStamina = Math.max(0, currentStamina - amount);
    }

    public void addYellowCard() {
        yellowCards++;
        if (yellowCards >= 2) {
            redCard = true;
            onPitch = false;
        }
    }

    public void giveRedCard() {
        redCard = true;
        onPitch = false;
    }

    public void injure() {
        injured = true;
        onPitch = false;
    }

    public void substituteOff() {
        onPitch = false;
    }

    public void substituteOn() {
        onPitch = true;
    }
}