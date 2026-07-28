package com.footballmanager.application.service.testharness;

import com.footballmanager.domain.model.entity.SessionPlayer;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

final class TestHarnessAutoSwapSupport {

    static final String AUTO_PLAYER_SWAP_STARTER = "__AUTO_STARTER";
    static final String AUTO_PLAYER_SWAP_BENCH = "__AUTO_BENCH";
    static final String AUTO_PLAYER_SWAP_PREFIX = "__AUTO_SWAP_";

    private TestHarnessAutoSwapSupport() {
    }

    static boolean isAutoToken(String playerId) {
        return AUTO_PLAYER_SWAP_STARTER.equals(playerId)
            || AUTO_PLAYER_SWAP_BENCH.equals(playerId)
            || (playerId != null && playerId.startsWith(AUTO_PLAYER_SWAP_PREFIX));
    }

    static String mode(String starterPlayerId, String benchPlayerId) {
        String token = starterPlayerId != null && starterPlayerId.startsWith(AUTO_PLAYER_SWAP_PREFIX)
            ? starterPlayerId
            : benchPlayerId;
        if (token == null || !token.startsWith(AUTO_PLAYER_SWAP_PREFIX)) {
            return "NATURAL";
        }
        return token.substring(AUTO_PLAYER_SWAP_PREFIX.length()).toUpperCase(Locale.ROOT);
    }

    static Optional<PlayerSwapAutoPair> choosePair(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench,
            String mode) {
        if (starters == null || bench == null || starters.isEmpty() || bench.isEmpty()) {
            return Optional.empty();
        }
        List<SessionPlayer> outfieldStarters = starters.stream()
            .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> impactSubPositionPriority(p.getPosition()))
                .thenComparingInt(TestHarnessAutoSwapSupport::substitutionScore)
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();
        List<SessionPlayer> outfieldBench = bench.stream()
            .filter(TestHarnessAutoSwapSupport::isOutfieldPlayer)
            .filter(p -> p.getSessionPlayerId() != null && !p.getSessionPlayerId().isBlank())
            .sorted(Comparator
                .comparingInt((SessionPlayer p) -> -substitutionScore(p))
                .thenComparing(SessionPlayer::getName, Comparator.nullsLast(String::compareTo)))
            .toList();

        Optional<PlayerSwapAutoPair> stressPair = switch (String.valueOf(mode).toUpperCase(Locale.ROOT)) {
            case "ATT_TO_DEF" -> chooseByLines(outfieldStarters, outfieldBench, "ATT", "DEF");
            case "DEF_TO_ATT" -> chooseByLines(outfieldStarters, outfieldBench, "DEF", "ATT");
            case "MID_TO_ATT" -> chooseByLines(outfieldStarters, outfieldBench, "MID", "ATT");
            case "MID_TO_DEF" -> chooseByLines(outfieldStarters, outfieldBench, "MID", "DEF");
            case "DOWNGRADE" -> chooseByOverallGap(outfieldStarters, outfieldBench, false);
            case "UPGRADE" -> chooseByOverallGap(outfieldStarters, outfieldBench, true);
            case "OUT_OF_LINE" -> chooseOutOfLine(outfieldStarters, outfieldBench);
            default -> Optional.empty();
        };
        if (stressPair.isPresent()) {
            return stressPair;
        }

        for (SessionPlayer starter : outfieldStarters) {
            Optional<SessionPlayer> samePosition = outfieldBench.stream()
                .filter(candidate -> samePosition(starter, candidate))
                .findFirst();
            if (samePosition.isPresent()) {
                return Optional.of(new PlayerSwapAutoPair(starter, samePosition.get()));
            }

            Optional<SessionPlayer> sameLine = outfieldBench.stream()
                .filter(candidate -> Objects.equals(
                    TestHarnessPixelSupport.autoLine(starter),
                    TestHarnessPixelSupport.autoLine(candidate)))
                .findFirst();
            if (sameLine.isPresent()) {
                return Optional.of(new PlayerSwapAutoPair(starter, sameLine.get()));
            }
        }

        if (!outfieldStarters.isEmpty() && !outfieldBench.isEmpty()) {
            return Optional.of(new PlayerSwapAutoPair(outfieldStarters.get(0), outfieldBench.get(0)));
        }
        return Optional.empty();
    }

    static int substitutionScore(SessionPlayer player) {
        if (player == null) {
            return 0;
        }
        String normalizedLine = TestHarnessPixelSupport.autoLine(player);
        if ("WINGER".equalsIgnoreCase(player.getPosition())
            || "LW".equalsIgnoreCase(player.getPosition())
            || "RW".equalsIgnoreCase(player.getPosition())) {
            return TestHarnessCommonSupport.safeInt(player.getAttack()) * 2
                + TestHarnessCommonSupport.safeInt(player.getSpeed()) * 2
                + TestHarnessCommonSupport.safeInt(player.getTechnique())
                + TestHarnessCommonSupport.safeInt(player.getMentality());
        }
        return switch (normalizedLine) {
            case "DEF" -> TestHarnessCommonSupport.safeInt(player.getDefense()) * 3
                + TestHarnessCommonSupport.safeInt(player.getMentality()) * 2
                + TestHarnessCommonSupport.safeInt(player.getSpeed())
                + TestHarnessCommonSupport.safeInt(player.getStamina());
            case "MID" -> TestHarnessCommonSupport.safeInt(player.getTechnique()) * 2
                + TestHarnessCommonSupport.safeInt(player.getMentality()) * 2
                + TestHarnessCommonSupport.safeInt(player.getAttack())
                + TestHarnessCommonSupport.safeInt(player.getDefense())
                + TestHarnessCommonSupport.safeInt(player.getStamina());
            case "ATT" -> TestHarnessCommonSupport.safeInt(player.getAttack()) * 3
                + TestHarnessCommonSupport.safeInt(player.getTechnique()) * 2
                + TestHarnessCommonSupport.safeInt(player.getSpeed())
                + TestHarnessCommonSupport.safeInt(player.getMentality());
            default -> TestHarnessCommonSupport.safeInt(player.getAttack())
                + TestHarnessCommonSupport.safeInt(player.getDefense())
                + TestHarnessCommonSupport.safeInt(player.getTechnique())
                + TestHarnessCommonSupport.safeInt(player.getSpeed())
                + TestHarnessCommonSupport.safeInt(player.getStamina())
                + TestHarnessCommonSupport.safeInt(player.getMentality());
        };
    }

    static int impactSubPositionPriority(String position) {
        if (position == null) return 9;
        return switch (position.toUpperCase(Locale.ROOT)) {
            case "ATT", "WINGER" -> 0;
            case "MID" -> 1;
            case "DEF" -> 2;
            default -> 3;
        };
    }

    static boolean samePosition(SessionPlayer off, SessionPlayer on) {
        return off != null
            && on != null
            && off.getPosition() != null
            && off.getPosition().equalsIgnoreCase(on.getPosition());
    }

    static boolean isOutfieldPlayer(SessionPlayer player) {
        return player != null
            && player.getSessionPlayerId() != null
            && !player.getSessionPlayerId().isBlank()
            && player.getPosition() != null
            && !"GK".equals(player.getPosition());
    }

    private static Optional<PlayerSwapAutoPair> chooseByLines(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench,
            String starterLine,
            String benchLine) {
        return starters.stream()
            .filter(starter -> starterLine.equals(TestHarnessPixelSupport.autoLine(starter)))
            .flatMap(starter -> bench.stream()
                .filter(candidate -> benchLine.equals(TestHarnessPixelSupport.autoLine(candidate)))
                .map(candidate -> new PlayerSwapAutoPair(starter, candidate)))
            .findFirst();
    }

    private static Optional<PlayerSwapAutoPair> chooseOutOfLine(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench) {
        return starters.stream()
            .flatMap(starter -> bench.stream()
                .filter(candidate -> !Objects.equals(
                    TestHarnessPixelSupport.autoLine(starter),
                    TestHarnessPixelSupport.autoLine(candidate)))
                .map(candidate -> new PlayerSwapAutoPair(starter, candidate)))
            .findFirst();
    }

    private static Optional<PlayerSwapAutoPair> chooseByOverallGap(
            List<SessionPlayer> starters,
            List<SessionPlayer> bench,
            boolean upgrade) {
        return starters.stream()
            .flatMap(starter -> bench.stream()
                .filter(candidate -> {
                    int delta = TestHarnessCommonSupport.playerOverall(candidate)
                        - TestHarnessCommonSupport.playerOverall(starter);
                    return upgrade ? delta >= 4 : delta <= -4;
                })
                .sorted((a, b) -> {
                    int deltaA = TestHarnessCommonSupport.playerOverall(a)
                        - TestHarnessCommonSupport.playerOverall(starter);
                    int deltaB = TestHarnessCommonSupport.playerOverall(b)
                        - TestHarnessCommonSupport.playerOverall(starter);
                    return upgrade ? Integer.compare(deltaB, deltaA) : Integer.compare(deltaA, deltaB);
                })
                .map(candidate -> new PlayerSwapAutoPair(starter, candidate)))
            .findFirst();
    }
}
