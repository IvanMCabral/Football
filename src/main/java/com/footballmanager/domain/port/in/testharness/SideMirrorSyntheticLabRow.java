package com.footballmanager.domain.port.in.testharness;

import com.footballmanager.domain.model.valueobject.TeamStyle;

import java.util.List;
import java.util.Map;

public record SideMirrorSyntheticLabRow(
    String formation,
    long seedStart,
    long seedEnd,
    int seedCount,
    double weakLeftWideXgL,
    double weakLeftWideXgR,
    double weakRightWideXgL,
    double weakRightWideXgR,
    double weakLeftWideShotsL,
    double weakLeftWideShotsR,
    double weakRightWideShotsL,
    double weakRightWideShotsR,
    double weakLeftRightEdge,
    double weakRightLeftEdge,
    double mirrorGap,
    String verdict,
    String read
) {}
