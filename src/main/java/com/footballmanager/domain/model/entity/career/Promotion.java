package com.footballmanager.domain.model.entity.career;

/**
 * Representa una promoción o descenso de equipo entre divisiones.
 */
public class Promotion {

    private String teamId;
    private String teamName;
    private String fromDivisionId;
    private String fromDivisionName;
    private String toDivisionId;
    private String toDivisionName;
    private PromotionType type;
    private int fromPosition;

    public enum PromotionType { PROMOTED, RELEGATED }

    public String getTeamId() { return teamId; }
    public void setTeamId(String teamId) { this.teamId = teamId; }
    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }
    public String getFromDivisionId() { return fromDivisionId; }
    public void setFromDivisionId(String fromDivisionId) { this.fromDivisionId = fromDivisionId; }
    public String getFromDivisionName() { return fromDivisionName; }
    public void setFromDivisionName(String fromDivisionName) { this.fromDivisionName = fromDivisionName; }
    public String getToDivisionId() { return toDivisionId; }
    public void setToDivisionId(String toDivisionId) { this.toDivisionId = toDivisionId; }
    public String getToDivisionName() { return toDivisionName; }
    public void setToDivisionName(String toDivisionName) { this.toDivisionName = toDivisionName; }
    public PromotionType getType() { return type; }
    public void setType(PromotionType type) { this.type = type; }
    public int getFromPosition() { return fromPosition; }
    public void setFromPosition(int fromPosition) { this.fromPosition = fromPosition; }
}
