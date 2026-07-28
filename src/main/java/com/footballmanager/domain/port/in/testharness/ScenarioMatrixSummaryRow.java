package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.application.service.domain.TeamStyle;

import java.util.List;
import java.util.Map;

public record ScenarioMatrixSummaryRow(
    String scenario,
    String actionType,
    String actionDetail,
    int seedCount,
    double avgUserXgDelta,
    double minUserXgDelta,
    double maxUserXgDelta,
    double avgOpponentXgDelta,
    double avgUserShotsDelta,
    double avgOpponentShotsDelta,
    double avgUserPossessionDelta,
    double avgUserCentralDelta,
    double avgUserWideDelta,
    double avgOpponentCentralDelta,
    double avgOpponentWideDelta,
    double avgUserCentralXgDelta,
    double avgUserWideXgDelta,
    double avgOpponentCentralXgDelta,
    double avgOpponentWideXgDelta,
    double avgUserLeftWideDelta,
    double avgUserRightWideDelta,
    double avgOpponentLeftWideDelta,
    double avgOpponentRightWideDelta,
    double avgUserLeftWideXgDelta,
    double avgUserRightWideXgDelta,
    double avgOpponentLeftWideXgDelta,
    double avgOpponentRightWideXgDelta,
    String baselineScenario,
    String baselineFormation,
    String changedFormation,
    boolean sameFormationAsBaseline
) {}
