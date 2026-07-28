package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.application.service.domain.TeamStyle;

import java.util.Map;

record ScenarioAction(
    ScenarioActionType type,
    TeamStyle changedStyle,
    String changedFormation,
    Map<String, LineupSlot> formationSlotsByPlayerId,
    PositionPlan positionPlan,
    SubPlan subPlan
) {
    static ScenarioAction none() {
        return new ScenarioAction(ScenarioActionType.NONE, null, null, null, null, null);
    }

    static ScenarioAction noopReplay() {
        return new ScenarioAction(ScenarioActionType.NOOP_REPLAY, null, null, null, null, null);
    }

    static ScenarioAction style(TeamStyle style) {
        return new ScenarioAction(ScenarioActionType.STYLE, style, null, null, null, null);
    }

    static ScenarioAction opponentStyle(TeamStyle style) {
        return new ScenarioAction(ScenarioActionType.OPPONENT_STYLE, style, null, null, null, null);
    }

    static ScenarioAction formation(String formation) {
        return formation(formation, null);
    }

    static ScenarioAction formation(String formation, Map<String, LineupSlot> slotsByPlayerId) {
        return new ScenarioAction(ScenarioActionType.FORMATION, null, formation, slotsByPlayerId, null, null);
    }

    static ScenarioAction position(PositionPlan positionPlan) {
        return new ScenarioAction(ScenarioActionType.POSITION, null, null, null, positionPlan, null);
    }

    static ScenarioAction substitution(SubPlan subPlan) {
        return new ScenarioAction(ScenarioActionType.SUBSTITUTION, null, null, null, null, subPlan);
    }

    static ScenarioAction positionAndSubstitution(PositionPlan positionPlan, SubPlan subPlan) {
        return new ScenarioAction(
            ScenarioActionType.POSITION_AND_SUBSTITUTION,
            null,
            null,
            null,
            positionPlan,
            subPlan);
    }

    String detail() {
        return switch (type) {
            case NONE -> "Sin cambios";
            case NOOP_REPLAY -> "Replay sin cambio";
            case STYLE -> changedStyle != null ? changedStyle.name() : "Style change";
            case OPPONENT_STYLE -> changedStyle != null
                ? "Opponent " + changedStyle.name()
                : "Opponent style change";
            case FORMATION -> changedFormation != null ? changedFormation : "Formation change";
            case POSITION -> positionPlan != null
                ? positionPlan.playerName() + " -> x"
                    + Math.round(positionPlan.xPercent()) + "/y"
                    + Math.round(positionPlan.yPercent())
                : "Position change";
            case SUBSTITUTION -> subPlan != null
                ? subPlan.offName() + " (" + subPlan.offPosition() + ") -> "
                    + subPlan.onName() + " (" + subPlan.onPosition() + ")"
                    + " [" + (subPlan.scoreDelta() >= 0 ? "+" : "") + subPlan.scoreDelta() + "]"
                : "Substitution";
            case POSITION_AND_SUBSTITUTION -> {
                String shape = positionPlan != null ? positionPlan.playerName() : "shape";
                String sub = subPlan != null
                    ? subPlan.offName() + " -> " + subPlan.onName()
                    : "substitution";
                yield shape + " + " + sub;
            }
        };
    }
}
