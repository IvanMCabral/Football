package com.footballmanager.domain.simulation.v32.state;

import com.footballmanager.domain.simulation.v32.enums.BallZone;
import com.footballmanager.domain.simulation.v32.enums.MatchPhase;
import com.footballmanager.domain.simulation.v32.enums.PlayerRole;

/**
 * Match state using Struct-of-Arrays (SoA) layout for cache efficiency.
 * All 22 players' data stored in parallel arrays for SIMD-friendly access.
 */
public final class MatchStateSoA {

    // ==================== BALL STATE ====================
    public float ballX;
    public float ballY;
    public float ballZ;
    public float ballVX;
    public float ballVY;
    public float ballVZ;

    /** Ball zone for quick lookup */
    public BallZone ballZone;

    /** Player index controlling ball (-1 if none) */
    public int ballController; // -1 = loose

    // ==================== MATCH STATE ====================
    public int homeTeamId;
    public int awayTeamId;
    public int homeScore;
    public int awayScore;
    public int currentMinute;
    public int currentSecond; // Milliseconds within minute
    public boolean matchOver;

    /** Current match phase */
    public MatchPhase phase;

    /** Tick count since match start */
    public long totalTicks;

    // ==================== PLAYER ARRAYS (22 players) ====================
    // Indices 0-10: Home team (0 = GK)
    // Indices 11-21: Away team (11 = GK)

    /** Player X positions */
    public final float[] playerX = new float[22];
    /** Player Y positions */
    public final float[] playerY = new float[22];
    /** Player Z positions (height, 0 = ground) */
    public final float[] playerZ = new float[22];

    /** Player velocity X */
    public final float[] playerVX = new float[22];
    /** Player velocity Y */
    public final float[] playerVY = new float[22];
    /** Player velocity Z (for jumps) */
    public final float[] playerVZ = new float[22];

    /** Player rotation angle (radians) */
    public final float[] playerRotation = new float[22];

    /** Player roles */
    public final PlayerRole[] playerRole = new PlayerRole[22];

    /** Player OVR ratings */
    public final byte[] playerOvr = new byte[22];

    /** Player energy levels (0-1) */
    public final float[] playerEnergy = new float[22];

    /** Player stamina levels (0-1) */
    public final float[] playerStamina = new float[22];

    /** Player team (0 = home, 1 = away) */
    public final byte[] playerTeam = new byte[22];

    // ==================== TACTICAL STATE ====================
    /** Home team formation index */
    public int homeFormation;
    /** Away team formation index */
    public int awayFormation;

    /** Home team tactical style (0=DEFEND, 1=COUNTER, 2=BALANCED, 3=ATTACK) */
    public byte homeTacticalStyle;
    /** Away team tactical style */
    public byte awayTacticalStyle;

    /** Home team pressing intensity (0-1) */
    public float homePressingIntensity;
    /** Away team pressing intensity */
    public float awayPressingIntensity;

    /** Home team momentum (0-1, 0.5 = neutral) */
    public float homeMomentum;
    /** Away team momentum */
    public float awayMomentum;

    // ==================== POSSESSION STATE ====================
    /** Possessing team (0=home, 1=away, -1=loose) */
    public int possessionTeam;
    /** Possessing player index (-1 if loose) */
    public int possessionPlayer;

    /** Home team possession percentage */
    public float homePossession;
    /** Away team possession percentage */
    public float awayPossession;

    /** Home team shots count */
    public int homeShots;
    /** Away team shots count */
    public int awayShots;

    /** Home team shots on target */
    public int homeShotsOnTarget;
    /** Away team shots on target */
    public int awayShotsOnTarget;

    /** Home team fouls */
    public int homeFouls;
    /** Away team fouls */
    public int awayFouls;

    // ==================== HALF TIME / EXTRA TIME ====================
    public boolean firstHalfOver;
    public int extraTimeMinutes;
    public boolean penalties;

    // ==================== WEATHER / CONDITIONS ====================
    /** Temperature in Celsius */
    public byte temperature;
    /** Humidity percentage */
    public byte humidity;
    /** Wind speed in m/s */
    public float windSpeed;

    public MatchStateSoA() {
        this.ballZone = BallZone.DEFENSIVE_ZONE;
        this.phase = MatchPhase.BUILD_UP;
        this.homeFormation = 0;
        this.awayFormation = 0;
        this.homeTacticalStyle = 2; // BALANCED
        this.awayTacticalStyle = 2;
        this.homePressingIntensity = 0.5f;
        this.awayPressingIntensity = 0.5f;
        this.homeMomentum = 0.5f;
        this.awayMomentum = 0.5f;
        this.possessionTeam = -1;
        this.possessionPlayer = -1;
        this.homePossession = 0.5f;
        this.awayPossession = 0.5f;
        this.temperature = 20;
        this.humidity = 50;
        this.windSpeed = 2.0f;
        this.extraTimeMinutes = 0;
        this.penalties = false;
    }

    // ==================== PLAYER ACCESSORS ====================

    public float getPlayerX(int idx) { return playerX[idx]; }
    public float getPlayerY(int idx) { return playerY[idx]; }
    public float getPlayerZ(int idx) { return playerZ[idx]; }
    public float getPlayerVX(int idx) { return playerVX[idx]; }
    public float getPlayerVY(int idx) { return playerVY[idx]; }
    public float getPlayerVZ(int idx) { return playerVZ[idx]; }
    public float getPlayerRotation(int idx) { return playerRotation[idx]; }
    public PlayerRole getPlayerRole(int idx) { return playerRole[idx]; }
    public byte getPlayerOvr(int idx) { return playerOvr[idx]; }
    public float getPlayerEnergy(int idx) { return playerEnergy[idx]; }
    public float getPlayerStamina(int idx) { return playerStamina[idx]; }
    public byte getPlayerTeam(int idx) { return playerTeam[idx]; }

    public void setPlayerX(int idx, float v) { playerX[idx] = v; }
    public void setPlayerY(int idx, float v) { playerY[idx] = v; }
    public void setPlayerZ(int idx, float v) { playerZ[idx] = v; }
    public void setPlayerVX(int idx, float v) { playerVX[idx] = v; }
    public void setPlayerVY(int idx, float v) { playerVY[idx] = v; }
    public void setPlayerVZ(int idx, float v) { playerVZ[idx] = v; }
    public void setPlayerRotation(int idx, float v) { playerRotation[idx] = v; }
    public void setPlayerEnergy(int idx, float v) { playerEnergy[idx] = v; }
    public void setPlayerStamina(int idx, float v) { playerStamina[idx] = v; }

    public float getPlayerStrength(int idx) {
        return 0.6f + playerEnergy[idx] * 0.4f;
    }

    public int getPlayerTeamSide(int idx) {
        return idx < 11 ? 0 : 1;
    }

    // ==================== CLONE ====================

    /**
     * Creates a deep copy of this state.
     */
    public MatchStateSoA copy() {
        MatchStateSoA copy = new MatchStateSoA();
        copy.ballX = this.ballX;
        copy.ballY = this.ballY;
        copy.ballZ = this.ballZ;
        copy.ballVX = this.ballVX;
        copy.ballVY = this.ballVY;
        copy.ballVZ = this.ballVZ;
        copy.ballZone = this.ballZone;
        copy.ballController = this.ballController;
        copy.homeTeamId = this.homeTeamId;
        copy.awayTeamId = this.awayTeamId;
        copy.homeScore = this.homeScore;
        copy.awayScore = this.awayScore;
        copy.currentMinute = this.currentMinute;
        copy.currentSecond = this.currentSecond;
        copy.matchOver = this.matchOver;
        copy.phase = this.phase;
        copy.totalTicks = this.totalTicks;
        System.arraycopy(this.playerX, 0, copy.playerX, 0, 22);
        System.arraycopy(this.playerY, 0, copy.playerY, 0, 22);
        System.arraycopy(this.playerZ, 0, copy.playerZ, 0, 22);
        System.arraycopy(this.playerVX, 0, copy.playerVX, 0, 22);
        System.arraycopy(this.playerVY, 0, copy.playerVY, 0, 22);
        System.arraycopy(this.playerVZ, 0, copy.playerVZ, 0, 22);
        System.arraycopy(this.playerRotation, 0, copy.playerRotation, 0, 22);
        System.arraycopy(this.playerRole, 0, copy.playerRole, 0, 22);
        System.arraycopy(this.playerOvr, 0, copy.playerOvr, 0, 22);
        System.arraycopy(this.playerEnergy, 0, copy.playerEnergy, 0, 22);
        System.arraycopy(this.playerStamina, 0, copy.playerStamina, 0, 22);
        System.arraycopy(this.playerTeam, 0, copy.playerTeam, 0, 22);
        copy.homeFormation = this.homeFormation;
        copy.awayFormation = this.awayFormation;
        copy.homeTacticalStyle = this.homeTacticalStyle;
        copy.awayTacticalStyle = this.awayTacticalStyle;
        copy.homePressingIntensity = this.homePressingIntensity;
        copy.awayPressingIntensity = this.awayPressingIntensity;
        copy.homeMomentum = this.homeMomentum;
        copy.awayMomentum = this.awayMomentum;
        copy.possessionTeam = this.possessionTeam;
        copy.possessionPlayer = this.possessionPlayer;
        copy.homePossession = this.homePossession;
        copy.awayPossession = this.awayPossession;
        copy.homeShots = this.homeShots;
        copy.awayShots = this.awayShots;
        copy.homeShotsOnTarget = this.homeShotsOnTarget;
        copy.awayShotsOnTarget = this.awayShotsOnTarget;
        copy.homeFouls = this.homeFouls;
        copy.awayFouls = this.awayFouls;
        copy.firstHalfOver = this.firstHalfOver;
        copy.extraTimeMinutes = this.extraTimeMinutes;
        copy.penalties = this.penalties;
        copy.temperature = this.temperature;
        copy.humidity = this.humidity;
        copy.windSpeed = this.windSpeed;
        return copy;
    }
}
