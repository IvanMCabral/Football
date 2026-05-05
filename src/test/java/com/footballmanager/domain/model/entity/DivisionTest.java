package com.footballmanager.domain.model.entity;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for Division assignment logic
 */
public class DivisionTest {
    
    @Test
    void testAssignTeamsToDivisions_8Teams_4PerDivision() {
        // 8 equipos, 4 por división = 2 divisiones de 4
        CareerSave career = createCareerWithTeams(8);
        career.assignTeamsToDivisions(4);
        
        assertEquals(2, career.getTotalDivisions());
        assertEquals(2, career.getSeasonManager().getDivisions().size());
        
        assertEquals(4, career.getSeasonManager().getDivisions().get(0).getTeamCount()); // Primera
        assertEquals(4, career.getSeasonManager().getDivisions().get(1).getTeamCount()); // Segunda
    }
    
    @Test
    void testAssignTeamsToDivisions_10Teams_4PerDivision() {
        // 10 equipos, 4 por división = 2 divisiones de 4 + 1 de 2
        CareerSave career = createCareerWithTeams(10);
        career.assignTeamsToDivisions(4);
        
        assertEquals(3, career.getTotalDivisions());
        assertEquals(3, career.getSeasonManager().getDivisions().size());
        
        assertEquals(4, career.getSeasonManager().getDivisions().get(0).getTeamCount()); // Primera
        assertEquals(4, career.getSeasonManager().getDivisions().get(1).getTeamCount()); // Segunda
        assertEquals(2, career.getSeasonManager().getDivisions().get(2).getTeamCount()); // Tercera
    }
    
    @Test
    void testAssignTeamsToDivisions_9Teams_4PerDivision() {
        // 9 equipos, 4 por división = 2 divisiones de 4 + 1 de 1 (no válida)
        CareerSave career = createCareerWithTeams(9);
        career.assignTeamsToDivisions(4);
        
        // 9 equipos = 2 divisiones de 4 = 8 equipos, 1 queda fuera (menos de 2)
        assertEquals(2, career.getTotalDivisions());
        assertEquals(2, career.getSeasonManager().getDivisions().size());
        
        assertEquals(4, career.getSeasonManager().getDivisions().get(0).getTeamCount());
        assertEquals(4, career.getSeasonManager().getDivisions().get(1).getTeamCount());
    }
    
    @Test
    void testAssignTeamsToDivisions_12Teams_4PerDivision() {
        // 12 equipos, 4 por división = 3 divisiones de 4
        CareerSave career = createCareerWithTeams(12);
        career.assignTeamsToDivisions(4);
        
        assertEquals(3, career.getTotalDivisions());
        assertEquals(3, career.getSeasonManager().getDivisions().size());
        
        assertEquals(4, career.getSeasonManager().getDivisions().get(0).getTeamCount());
        assertEquals(4, career.getSeasonManager().getDivisions().get(1).getTeamCount());
        assertEquals(4, career.getSeasonManager().getDivisions().get(2).getTeamCount());
    }
    
    @Test
    void testAssignTeamsToDivisions_5Teams_2PerDivision() {
        // 5 equipos, 2 por división = 2 divisiones de 2 + 1 de 1 (no válida)
        CareerSave career = createCareerWithTeams(5);
        career.assignTeamsToDivisions(2);
        
        // 5 equipos = 2 divisiones de 2 = 4 equipos, 1 queda fuera
        assertEquals(2, career.getTotalDivisions());
        assertEquals(2, career.getSeasonManager().getDivisions().size());
    }
    
    @Test
    void testAssignTeamsToDivisions_6Teams_3PerDivision() {
        // 6 equipos, 3 por división = 2 divisiones de 3
        CareerSave career = createCareerWithTeams(6);
        career.assignTeamsToDivisions(3);
        
        assertEquals(2, career.getTotalDivisions());
        assertEquals(2, career.getSeasonManager().getDivisions().size());
        
        assertEquals(3, career.getSeasonManager().getDivisions().get(0).getTeamCount());
        assertEquals(3, career.getSeasonManager().getDivisions().get(1).getTeamCount());
    }
    
    @Test
    void testAssignTeamsToDivisions_AllTeamsInValidDivisions() {
        // Verificar que todos los equipos estén en alguna división
        CareerSave career = createCareerWithTeams(10);
        career.assignTeamsToDivisions(4);
        
        List<String> allTeamsInDivisions = new ArrayList<>();
        for (Division division : career.getSeasonManager().getDivisions()) {
            allTeamsInDivisions.addAll(division.getTeamIds());
        }
        
        // 10 equipos en divisiones (2 de 4 + 1 de 2)
        assertEquals(10, allTeamsInDivisions.size());
    }
    
    @Test
    void testAssignTeamsToDivisions_NoDuplicateTeams() {
        // Verificar que no haya equipos duplicados en divisiones
        CareerSave career = createCareerWithTeams(8);
        career.assignTeamsToDivisions(4);
        
        Set<String> allTeamIds = new HashSet<>();
        for (Division division : career.getSeasonManager().getDivisions()) {
            for (String teamId : division.getTeamIds()) {
                assertFalse(allTeamIds.contains(teamId), "Team duplicated across divisions");
                allTeamIds.add(teamId);
            }
        }
        
        // Todos los teamIds únicos deben ser igual al número de equipos en divisiones
        assertEquals(8, allTeamIds.size());
    }
    
    private CareerSave createCareerWithTeams(int teamCount) {
        CareerSave career = new CareerSave();
        
        // Crear equipos con diferentes OVRs
        for (int i = 0; i < teamCount; i++) {
            SessionTeam team = new SessionTeam();
            team.setSessionTeamId(UUID.randomUUID().toString());
            team.setName("Team " + i);
            team.setCountry("Test");
            team.setBudget(new BigDecimal("10000000"));
            
            // Calcular OVR de los jugadores
            int baseOVR = 50 + (i * 5); // Teams 0-9 tienen OVR 50, 55, 60, 65, 70, etc.
            createPlayersForTeam(career, team, baseOVR);
            
            career.addSessionTeam(team);
            career.setUserSessionTeamId(team.getSessionTeamId());
        }
        
        return career;
    }
    
    private void createPlayersForTeam(CareerSave career, SessionTeam team, int ovr) {
        // Crear 11 jugadores con OVR promedio igual al parámetro
        for (int j = 0; j < 11; j++) {
            SessionPlayer player = new SessionPlayer();
            player.setSessionPlayerId(UUID.randomUUID().toString());
            player.setName("Player " + team.getSessionTeamId() + "_" + j);
            
            // Calcular attack/defense para obtener el OVR deseado
            int attack = ovr + (j % 3) - 1; // Variación pequeña
            int defense = (ovr * 2) - attack;
            
            player.setAttack(attack);
            player.setDefense(defense);
            player.setPosition(j < 4 ? "DEF" : (j < 8 ? "MID" : "FWD"));
            
            career.addSessionPlayer(player);
            career.assignPlayerToTeam(player.getSessionPlayerId(), team.getSessionTeamId());
        }
    }
}
