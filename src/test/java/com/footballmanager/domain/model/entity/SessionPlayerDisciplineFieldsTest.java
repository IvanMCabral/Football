package com.footballmanager.domain.model.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V24D6D2: Tests for SessionPlayer discipline/suspension fields.
 * Fields added: yellowCards, redCards, suspended, suspensionRemainingMatches.
 */
class SessionPlayerDisciplineFieldsTest {

    @Test
    void newSessionPlayer_defaultsDisciplineFields() {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());
        player.setName("Test Player");
        player.setPosition("MID");

        assertEquals(0, player.getYellowCards());
        assertEquals(0, player.getRedCards());
        assertFalse(player.getSuspended());
        assertEquals(0, player.getSuspensionRemainingMatches());
    }

    @Test
    void newSessionPlayer_defaultsFromFactoryMethod() {
        SessionPlayer player = SessionPlayer.fromWorldPlayer("wp-1", "John Doe", "ATT", 25, 75);

        assertEquals(0, player.getYellowCards());
        assertEquals(0, player.getRedCards());
        assertFalse(player.getSuspended());
        assertEquals(0, player.getSuspensionRemainingMatches());
    }

    @Test
    void gettersSetters_disciplineFields_work() {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());

        player.setYellowCards(3);
        player.setRedCards(1);
        player.setSuspended(true);
        player.setSuspensionRemainingMatches(1);

        assertEquals(3, player.getYellowCards());
        assertEquals(1, player.getRedCards());
        assertTrue(player.getSuspended());
        assertEquals(1, player.getSuspensionRemainingMatches());
    }

    @Test
    void sessionPlayerWithDiscipline_serializesAndDeserializesCorrectly() throws Exception {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId("player- Discipline-001");
        player.setWorldPlayerId("wp-discipline");
        player.setName("Suspended Star");
        player.setPosition("ATT");
        player.setAttack(80);
        player.setDefense(40);
        player.setTechnique(70);
        player.setSpeed(75);
        player.setStamina(80);
        player.setMentality(65);
        player.setMarketValue(BigDecimal.valueOf(5_000_000));
        player.setEnergy(85);
        player.setForm(60);
        player.setYellowCards(4);
        player.setRedCards(1);
        player.setSuspended(true);
        player.setSuspensionRemainingMatches(2);

        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();

        String json = mapper.writeValueAsString(player);
        SessionPlayer deserialized = mapper.readValue(json, SessionPlayer.class);

        assertEquals(4, deserialized.getYellowCards());
        assertEquals(1, deserialized.getRedCards());
        assertTrue(deserialized.getSuspended());
        assertEquals(2, deserialized.getSuspensionRemainingMatches());
        assertEquals("Suspended Star", deserialized.getName());
        assertEquals("ATT", deserialized.getPosition());
    }

    @Test
    void nullYellowCards_defaultsToZero() throws Exception {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());

        java.lang.reflect.Field field = SessionPlayer.class.getDeclaredField("yellowCards");
        field.setAccessible(true);
        field.set(player, null);

        assertEquals(0, player.getYellowCards());
    }

    @Test
    void nullRedCards_defaultsToZero() throws Exception {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());

        java.lang.reflect.Field field = SessionPlayer.class.getDeclaredField("redCards");
        field.setAccessible(true);
        field.set(player, null);

        assertEquals(0, player.getRedCards());
    }

    @Test
    void nullSuspended_defaultsToFalse() throws Exception {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());

        java.lang.reflect.Field field = SessionPlayer.class.getDeclaredField("suspended");
        field.setAccessible(true);
        field.set(player, null);

        assertFalse(player.getSuspended());
    }

    @Test
    void nullSuspensionRemainingMatches_defaultsToZero() throws Exception {
        SessionPlayer player = new SessionPlayer();
        player.setSessionPlayerId(UUID.randomUUID().toString());

        java.lang.reflect.Field field = SessionPlayer.class.getDeclaredField("suspensionRemainingMatches");
        field.setAccessible(true);
        field.set(player, null);

        assertEquals(0, player.getSuspensionRemainingMatches());
    }
}