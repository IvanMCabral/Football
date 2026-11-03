package com.footballmanager.application.service.simulation.detailed;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.footballmanager.application.service.editor.FormationService;
import com.footballmanager.domain.model.entity.SessionPlayer;
import com.footballmanager.domain.model.entity.SessionTeam;
import com.footballmanager.domain.model.valueobject.LineupSlot;
import com.footballmanager.domain.model.valueobject.TeamStyle;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Local engine experiment: synthetic squads, no Redis, network or real careers. */
class ManagerTournamentExperimentTest {
    private static final String[] ROLES = {"GK","LB","CB","CB","RB","LM","CM","CM","RM","ST","ST"};
    private static final String[] BENCH = {"GK","CB","LB","CM","RM","ST","ST"};
    private static final String MANAGER = "club-0";

    @Test void managedSideNeverAutoSubstitutesButRivalStillDoesOnBothSides() {
        for (boolean home : List.of(true, false)) {
            int rivals = 0;
            for (int seed = 0; seed < 12; seed++) {
                var c = context(home ? 0 : 1, home ? 1 : 0).withManualSubstitutionTeam(MANAGER);
                c = c.withNewFormation(MANAGER,"4-4-2").withNewStyle(MANAGER,TeamStyle.BALANCED)
                    .withSlots(MANAGER, home ? c.homeSlotsByPlayerId() : c.awaySlotsByPlayerId());
                var session = new LiveSession(c,seed);
                assertThat(session.context().manualSubstitutionTeamId()).isEqualTo(MANAGER);
                var result = session.finalResult();
                assertThat(result.timeline().events()).noneMatch(e -> e.type()==DetailedMatchEventType.SUBSTITUTION && MANAGER.equals(e.teamId()));
                rivals += result.timeline().events().stream().filter(e -> e.type()==DetailedMatchEventType.SUBSTITUTION).count();
            }
            assertThat(rivals).isPositive();
        }
    }

    @Test void ownershipAndGeometrySurviveJsonAndInvalidOwnerIsRejected() throws Exception {
        var c=context(0,1).withManualSubstitutionTeam(MANAGER);
        var mapper=new ObjectMapper().findAndRegisterModules();
        var decoded=mapper.readValue(mapper.writeValueAsString(c),MatchContext.class);
        assertThat(decoded.manualSubstitutionTeamId()).isEqualTo(MANAGER);
        assertThat(decoded.homeSlotsByPlayerId()).isEqualTo(c.homeSlotsByPlayerId());
        assertThatThrownBy(() -> c.withManualSubstitutionTeam("stranger")).isInstanceOf(IllegalArgumentException.class);
        assertThat(context(1,2).allowsAutomaticSubstitutions("club-1")).isTrue();
    }

    @Test void liveMoveRetainsNaturalRoleAndLowersOutOfPositionEffectiveness() {
        var c=context(0,1);var player=c.homeStartingPlayers().get(2);String id=player.getSessionPlayerId();
        var engine=new LiveSession(c,42L);
        engine.changeFormation(MANAGER,"4-4-2",Map.of(id,"ATT"),Map.of(id,new LineupSlot(id,"LIVE-10",50.0,17.0)));
        assertThat(engine.context().homeStartingPlayers().get(2).getPosition()).isEqualTo("CB");
        var state=TeamMatchState.create(engine.context().homeTeam(),engine.context().homeStartingPlayers(),engine.context().homeBenchPlayers(),TeamStyle.BALANCED,engine.context().homeSlotsByPlayerId());
        var moved=state.startingPlayers().stream().filter(p->p.sessionPlayerId().equals(id)).findFirst().orElseThrow();
        assertThat(moved.naturalPosition()).isEqualTo("CB");assertThat(moved.position()).isEqualTo("ATT");
        var effectiveness=new TacticalEffectivenessService(new TacticalPositionService());
        assertThat(effectiveness.tacticalEffectiveness(moved,engine.context().homeSlotsByPlayerId())).isLessThan(0.6);
    }

    @Test void replacementActuallyJoinsTheActiveElevenAndInheritsRoleWithoutChangingSpecialty() {
        var c=context(0,1);var state=TeamMatchState.create(c.homeTeam(),c.homeStartingPlayers(),c.homeBenchPlayers(),TeamStyle.BALANCED,c.homeSlotsByPlayerId());
        var off=state.startingPlayers().get(10);var on=state.benchPlayers().get(1);
        new SubstitutionEngine().manualSubstitute(state,off.sessionPlayerId(),on.sessionPlayerId(),60);
        assertThat(state.startingPlayers()).hasSize(11).contains(on).doesNotContain(off);
        assertThat(state.benchPlayers()).contains(off).doesNotContain(on);
        assertThat(on.position()).isEqualTo("ATT");assertThat(on.naturalPosition()).isEqualTo("CB");
        assertThat(state.startingPlayers().stream().filter(PlayerMatchState::onPitch).count()).isEqualTo(11);
    }

    @Test void fullTwentyClubTournamentsAndThirtyEightInteractiveMatches() throws Exception {
        Map<String,Object> report=new LinkedHashMap<>();
        Map<String,List<Map<String,Object>>> experiments=new LinkedHashMap<>();
        for(String scenario:List.of("natural","manual-substitutions","manual-out-of-role","advanced-midfield","defender-striker-swap")) {
            List<Map<String,Object>> seasons=new ArrayList<>();
            for(int repeat=0;repeat<3;repeat++) {
                int[][] table=new int[20][5]; // played, points, goals for, goals against, wins
                List<Map<String,Object>> ownMatches=new ArrayList<>();
                int matches=0, substitutions=0;
                Set<String> pairs=new HashSet<>();
                for(var fixture:schedule()) {
                    int home=fixture[1],away=fixture[2];
                    var c=context(home,away);
                    boolean own=home==0||away==0;
                    if(own) c=change(c,scenario);
                    var result=new DetailedMatchEngine().simulate(c,20261103L+repeat*10000L+home*20L+away);
                    int hg=result.homeGoals(),ag=result.awayGoals();
                    assertThat(result.timeline().events().stream().filter(e->e.type()==DetailedMatchEventType.GOAL && e.teamId().equals(result.homeTeamId())).count()).isEqualTo(hg);
                    assertThat(result.timeline().events().stream().filter(e->e.type()==DetailedMatchEventType.GOAL && e.teamId().equals(result.awayTeamId())).count()).isEqualTo(ag);
                    table[home][0]++;table[away][0]++;table[home][2]+=hg;table[home][3]+=ag;table[away][2]+=ag;table[away][3]+=hg;
                    if(hg>ag){table[home][1]+=3;table[home][4]++;}else if(hg<ag){table[away][1]+=3;table[away][4]++;}else{table[home][1]++;table[away][1]++;}
                    if(own){
                        int ownSubs=(int)result.timeline().events().stream().filter(e->e.type()==DetailedMatchEventType.SUBSTITUTION && MANAGER.equals(e.teamId())).count();
                        if(!scenario.startsWith("manual-"))assertThat(ownSubs).isZero();
                        substitutions+=ownSubs;
                        ownMatches.add(Map.of("round",fixture[0],"home",home,"away",away,"homeGoals",hg,"awayGoals",ag,"shots",home==0?result.homeShots():result.awayShots(),"xg",home==0?result.homeXg():result.awayXg(),"subs",ownSubs));
                    }
                    assertThat(pairs.add(home+":"+away)).isTrue();matches++;
                }
                assertThat(matches).isEqualTo(380);assertThat(ownMatches).hasSize(38);
                for(var row:table)assertThat(row[0]).isEqualTo(38);
                assertThat(Arrays.stream(table).mapToInt(r->r[2]).sum()).isEqualTo(Arrays.stream(table).mapToInt(r->r[3]).sum());
                seasons.add(Map.of("seedBatch",repeat,"matches",matches,"table",table,"managerMatches",ownMatches,"manualSubstitutions",substitutions));
            }
            experiments.put(scenario,seasons);
        }
        // Actual live session edits at minute 30 and manual substitutions at 60, home and away.
        int applied=0, moved=0, skippedInjuredOrDismissed=0, historyChanges=0;
        List<Map<String,Object>> interactive=new ArrayList<>();
        for(int opponent=1;opponent<20;opponent++)for(boolean home:List.of(true,false)) {
            var c=context(home?0:opponent,home?opponent:0);
            var session=new LiveSession(c,88000L+opponent*2+(home?0:1));
            for(int minute=1;minute<=90;minute++) {
                var snapshot=session.tick();
                if(minute==30){
                    var slots=new LinkedHashMap<>(home?c.homeSlotsByPlayerId():c.awaySlotsByPlayerId());
                    var p=(home?c.homeStartingPlayers():c.awayStartingPlayers()).get(6);
                    var slot=slots.get(p.getSessionPlayerId());
                    slots.put(p.getSessionPlayerId(),new LineupSlot(p.getSessionPlayerId(),slot.subdivisionId(),72.25,44.5));
                    var pastGoals=snapshot.allEvents().stream().filter(e->e.type()==DetailedMatchEventType.GOAL).toList();
                    session.changeFormation(MANAGER,"4-4-2",Map.of(),slots);moved++;
                    if(!session.snapshot().allEvents().stream().filter(e->e.type()==DetailedMatchEventType.GOAL).toList().equals(pastGoals))historyChanges++;
                }
                if(minute==60 || minute==75 || (minute==80 && opponent%3==0)){
                    int offIndex=minute==60?10:minute==75?7:9;
                    int onIndex=minute==60?5:minute==75?3:1;
                    var pastGoals=snapshot.allEvents().stream().filter(e->e.type()==DetailedMatchEventType.GOAL).toList();
                    var slots=home?snapshot.homeSlots():snapshot.awaySlots();
                    var off=(home?c.homeStartingPlayers():c.awayStartingPlayers()).get(offIndex);
                    var on=(home?c.homeBenchPlayers():c.awayBenchPlayers()).get(onIndex);
                    boolean unavailable=snapshot.allEvents().stream().anyMatch(e->MANAGER.equals(e.teamId()) && off.getSessionPlayerId().equals(e.playerId()) && (e.type()==DetailedMatchEventType.RED_CARD||e.type()==DetailedMatchEventType.INJURY));
                    if(slots.stream().anyMatch(s->s.playerId().equals(off.getSessionPlayerId()))&&!unavailable){
                        session.recordManualSubstitution(new DetailedMatchEvent(minute,DetailedMatchEventType.SUBSTITUTION,MANAGER,off.getSessionPlayerId(),off.getName(),on.getSessionPlayerId(),on.getName(),0,"Manual tournament QA"));applied++;
                    }else skippedInjuredOrDismissed++;
                    assertThat(session.snapshot().allEvents().stream().filter(e->e.type()==DetailedMatchEventType.GOAL).toList()).as("manual substitution preserves past goals").isEqualTo(pastGoals);
                }
            }
            var finalSnapshot=session.snapshot();var result=session.finalResult();
            assertThat(finalSnapshot.homeGoals()).as("home opponent %s managerHome %s",opponent,home).isEqualTo(result.homeGoals());assertThat(finalSnapshot.awayGoals()).as("away opponent %s managerHome %s",opponent,home).isEqualTo(result.awayGoals());
            var subs=finalSnapshot.allEvents().stream().filter(e->e.type()==DetailedMatchEventType.SUBSTITUTION && MANAGER.equals(e.teamId())).toList();
            assertThat(subs).allMatch(e->e.description().contains("Manual tournament QA"));
            interactive.add(Map.of("opponent",opponent,"managerHome",home,"homeGoals",result.homeGoals(),"awayGoals",result.awayGoals(),"managerSubs",subs.size()));
        }
        assertThat(historyChanges).as("tactical commands never rewrite goals already shown").isZero();
        assertThat(moved).isEqualTo(38);assertThat(applied).isPositive();
        report.put("experiments",experiments);report.put("interactive",interactive);
        report.put("interactiveChanges",Map.of("moves",moved,"manualSubs",applied,"unavailableSkipped",skippedInjuredOrDismissed,"historicalGoalChangesAfterMove",historyChanges));
        report.put("scope","Local real match engine; synthetic fresh squads per fixture; no HTTP/UI, Redis, persistent fatigue, injuries or suspensions between fixtures");
        Files.createDirectories(Path.of("target/qa"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of("target/qa/manager-tournament.json").toFile(),report);
    }

    private List<int[]> schedule(){
        List<int[]> games=new ArrayList<>();List<Integer> ring=new ArrayList<>();
        for(int i=0;i<20;i++)ring.add(i);
        for(int round=0;round<19;round++){
            for(int i=0;i<10;i++){
                int a=ring.get(i),b=ring.get(19-i);if((round+i)%2==1){int t=a;a=b;b=t;}
                games.add(new int[]{round+1,a,b});games.add(new int[]{round+20,b,a});
            }
            ring.add(1,ring.remove(19));
        }
        games.sort(Comparator.comparingInt(g->g[0]));
        assertThat(games).hasSize(380);
        for(int round=1;round<=38;round++){
            Set<Integer> clubs=new HashSet<>();for(var g:games)if(g[0]==round){assertThat(clubs.add(g[1])).isTrue();assertThat(clubs.add(g[2])).isTrue();}
            assertThat(clubs).hasSize(20);
        }
        return games;
    }
    private MatchContext change(MatchContext c,String scenario){
        var players=c.homeTeamId().equals(MANAGER)?c.homeStartingPlayers():c.awayStartingPlayers();
        var bench=c.homeTeamId().equals(MANAGER)?c.homeBenchPlayers():c.awayBenchPlayers();
        var slots=new LinkedHashMap<>(c.homeTeamId().equals(MANAGER)?c.homeSlotsByPlayerId():c.awaySlotsByPlayerId());
        if(scenario.equals("manual-out-of-role"))return c.withManualSubstitution(MANAGER,players.get(10).getSessionPlayerId(),bench.get(1).getSessionPlayerId(),60);
        if(scenario.equals("manual-substitutions"))return c.withManualSubstitution(MANAGER,players.get(10).getSessionPlayerId(),bench.get(5).getSessionPlayerId(),60);
        if(scenario.equals("advanced-midfield"))for(int index:List.of(6,7)){
            var id=players.get(index).getSessionPlayerId();var slot=slots.get(id);slots.put(id,new LineupSlot(id,slot.subdivisionId(),slot.customXPercent(),35.0));
        }
        if(scenario.equals("defender-striker-swap")){
            var a=players.get(2).getSessionPlayerId();var b=players.get(10).getSessionPlayerId();var sa=slots.get(a);var sb=slots.get(b);
            slots.put(a,new LineupSlot(a,sb.subdivisionId(),sb.customXPercent(),sb.customYPercent()));slots.put(b,new LineupSlot(b,sa.subdivisionId(),sa.customXPercent(),sa.customYPercent()));
        }
        return c.withSlots(MANAGER,slots);
    }
    private MatchContext context(int home,int away){
        var ht=team(home);var at=team(away);var hp=players(home,false);var ap=players(away,false);
        var c=new MatchContext("fixture-"+home+"-"+away,ht.getSessionTeamId(),at.getSessionTeamId(),ht,at,hp,ap,players(home,true),players(away,true),"4-4-2","4-4-2",TeamStyle.BALANCED,TeamStyle.BALANCED,List.of(),slots(hp),slots(ap));
        return home==0||away==0?c.withManualSubstitutionTeam(MANAGER):c;
    }
    private SessionTeam team(int club){var t=SessionTeam.fromRealTeam(UUID.nameUUIDFromBytes(("club-"+club).getBytes()),"world-"+club,"Club "+club,"QA",BigDecimal.ZERO,"4-4-2",null);t.setSessionTeamId("club-"+club);return t;}
    private List<SessionPlayer> players(int club,boolean bench){
        List<SessionPlayer> list=new ArrayList<>();var roles=bench?BENCH:ROLES;
        for(int i=0;i<roles.length;i++){String id="club-"+club+(bench?"-bench-":"-starter-")+i;var p=SessionPlayer.fromWorldPlayer(id,id,roles[i],25,68+club%9+(bench?5:0));p.setSessionPlayerId(id);list.add(p);}return list;
    }
    private Map<String,LineupSlot> slots(List<SessionPlayer> players){
        Map<String,LineupSlot> slots=new LinkedHashMap<>();var positions=new FormationService().getAllFormations().stream().filter(f->f.name().equals("4-4-2")).findFirst().orElseThrow().positions();
        for(int i=0;i<11;i++){var p=players.get(i);var xy=positions.get(i);slots.put(p.getSessionPlayerId(),new LineupSlot(p.getSessionPlayerId(),xy.subdivisionId(),xy.xPercent(),xy.yPercent()));}return slots;
    }
}
