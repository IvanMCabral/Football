package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record LineupWidthDiagnostic(
    int leftCount,
    int centerCount,
    int rightCount,
    int wideCount,
    double leftAvgX,
    double rightAvgX,
    double widthScore,
    double sideBalance,
    String verdict,
    String read
) {}
