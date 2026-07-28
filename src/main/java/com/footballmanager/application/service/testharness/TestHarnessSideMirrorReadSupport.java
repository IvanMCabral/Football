package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.port.in.testharness.FormationMatrixSummaryRow;
import com.footballmanager.domain.port.in.testharness.SideMirrorSyntheticLabRow;

final class TestHarnessSideMirrorReadSupport {

    private TestHarnessSideMirrorReadSupport() {}

    static SideMirrorSyntheticLabRow toSyntheticSideMirrorRow(
            String formation,
            long seedStart,
            int seedCount,
            FormationMatrixSummaryRow weakLeft,
            FormationMatrixSummaryRow weakRight) {
        double weakLeftRightEdge = TestHarnessCommonSupport.round3(
            weakLeft.avgRightWideXgFor() - weakLeft.avgLeftWideXgFor());
        double weakRightLeftEdge = TestHarnessCommonSupport.round3(
            weakRight.avgLeftWideXgFor() - weakRight.avgRightWideXgFor());
        double mirrorGap = TestHarnessCommonSupport.round3(weakLeftRightEdge - weakRightLeftEdge);
        boolean weakLeftOk = weakLeftRightEdge >= 0.015;
        boolean weakRightOk = weakRightLeftEdge >= 0.015;
        boolean lowBlock = "5-4-1".equals(formation)
            && Math.abs(mirrorGap) <= 0.025
            && !weakLeftOk
            && !weakRightOk;
        String verdict = weakLeftOk && weakRightOk
            ? "OK"
            : (weakLeftOk || weakRightOk || lowBlock ? "Parcial" : "Revisar");
        String read = "OK".equals(verdict)
            ? "Laboratorio sintetico espejo responde en ambos sentidos."
            : "Parcial".equals(verdict)
                ? partialRead(formation, lowBlock)
                : "Sin senal lateral suficiente en laboratorio sintetico; revisar motor.";
        return new SideMirrorSyntheticLabRow(
            formation,
            seedStart,
            seedStart + seedCount - 1L,
            seedCount,
            weakLeft.avgLeftWideXgFor(),
            weakLeft.avgRightWideXgFor(),
            weakRight.avgLeftWideXgFor(),
            weakRight.avgRightWideXgFor(),
            weakLeft.avgLeftWideShotsFor(),
            weakLeft.avgRightWideShotsFor(),
            weakRight.avgLeftWideShotsFor(),
            weakRight.avgRightWideShotsFor(),
            weakLeftRightEdge,
            weakRightLeftEdge,
            mirrorGap,
            verdict,
            read);
    }

    private static String partialRead(String formation, boolean lowBlock) {
        if (lowBlock) {
            return "5-4-1 bloque bajo: baja senal lateral ofensiva esperable; "
                + "validar con low block lab antes de tocar motor.";
        }
        return switch (formation) {
            case "3-5-2" -> "3-5-2: un carril responde y el otro queda plano; "
                + "validar carrileros/seeds antes de tocar motor.";
            case "4-2-2-2" -> "4-2-2-2: sin carrileros naturales; la amplitud depende "
                + "de mediapuntas/delanteros y puede responder asimetrica.";
            default -> "Un lado responde mas que el otro aun sin sesgo de plantel; "
                + "revisar calibracion lateral.";
        };
    }
}
