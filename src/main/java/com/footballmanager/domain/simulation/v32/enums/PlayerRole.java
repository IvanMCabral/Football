package com.footballmanager.domain.simulation.v32.enums;

/**
 * Player roles in formation for V32 simulation.
 */
public enum PlayerRole {
    /** Goalkeeper */
    GK(0),
    /** Right back */
    RB(1),
    /** Left back */
    LB(2),
    /** Center back */
    CB(3),
    /** Defensive midfielder */
    DM(4),
    /** Central midfielder */
    CM(5),
    /** Right midfielder */
    RM(6),
    /** Left midfielder */
    LM(7),
    /** Attacking midfielder */
    AM(8),
    /** Right winger */
    RW(9),
    /** Left winger */
    LW(10),
    /** Center forward */
    ST(11);

    private final byte id;

    PlayerRole(int id) {
        this.id = (byte) id;
    }

    public byte getId() { return id; }

    public static PlayerRole fromId(int id) {
        for (PlayerRole role : values()) {
            if (role.id == id) return role;
        }
        return CM;
    }

    /** @return true if this is a defensive role */
    public boolean isDefensive() {
        return this == GK || this == RB || this == LB || this == CB || this == DM;
    }

    /** @return true if this is an attacking role */
    public boolean isAttacking() {
        return this == ST || this == RW || this == LW || this == AM;
    }

    /** @return true if this is a midfielder role */
    public boolean isMidfield() {
        return this == CM || this == RM || this == LM || this == DM || this == AM;
    }
}
