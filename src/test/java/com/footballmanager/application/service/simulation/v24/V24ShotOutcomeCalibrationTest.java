package com.footballmanager.application.service.simulation.v24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V24ShotOutcomeCalibrationTest {

    @Test
    @DisplayName("shot-on-target probability grows with xG")
    void onTargetProbability_isMonotonicWithXg() {
        double low = V24DetailedMatchEngine.onTargetProbability(0.02);
        double mid = V24DetailedMatchEngine.onTargetProbability(0.20);
        double high = V24DetailedMatchEngine.onTargetProbability(0.60);

        assertTrue(low < mid, "a better chance must be more likely to hit the target than a low-xG attempt");
        assertTrue(mid < high, "an elite chance must be more likely to hit the target than a medium chance");
        assertTrue(low >= 0.38 && high <= 0.54, "calibration should stay inside professional bounds");
    }
}
