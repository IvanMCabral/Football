package com.footballmanager.application.service.query;

import com.footballmanager.adapters.in.web.world.dto.DivisionPreview;
import com.footballmanager.adapters.in.web.world.dto.TeamWithOVR;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio para calculos de preview de divisiones.
 * Extraido de WorldController para cumplir SRP.
 */
@Service
public class DivisionPreviewService {

    /**
     * Calcula el preview de como quedarian las divisiones con X equipos por division
     */
    public List<DivisionPreview> calculateDivisionPreview(
            List<TeamWithOVR> teamsWithOVR,
            int teamsPerDivision) {

        List<DivisionPreview> previews = new ArrayList<>();
        int totalTeams = teamsWithOVR.size();
        int fullDivisions = totalTeams / teamsPerDivision;
        int remainder = totalTeams % teamsPerDivision;

        int currentIndex = 0;
        for (int i = 0; i < fullDivisions; i++) {
            List<TeamWithOVR> divisionTeams = teamsWithOVR.subList(currentIndex, currentIndex + teamsPerDivision);
            currentIndex += teamsPerDivision;

            previews.add(new DivisionPreview(
                    i + 1,
                    getDivisionName(i + 1),
                    divisionTeams
            ));
        }

        // Ultima division con equipos sobrantes (si hay 2+)
        if (remainder >= 2) {
            List<TeamWithOVR> divisionTeams = teamsWithOVR.subList(currentIndex, totalTeams);
            previews.add(new DivisionPreview(
                    fullDivisions + 1,
                    getDivisionName(fullDivisions + 1),
                    divisionTeams
            ));
        }

        return previews;
    }

    /**
     * Obtiene el nombre de una division por su numero
     */
    public String getDivisionName(int number) {
        return switch (number) {
            case 1 -> "Primera";
            case 2 -> "Segunda";
            case 3 -> "Tercera";
            default -> number + "ª";
        };
    }
}
