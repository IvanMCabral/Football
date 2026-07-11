package com.footballmanager.domain.model.valueobject;

import com.footballmanager.adapters.in.web.career.lineup.dto.LineupSlotDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V25D99.20.13-BACK: computes simple "societies" between nearby players.
 *
 * <p>MVP scope: slot category, pitch distance and channel coverage. Player
 * quality/skills are still represented by {@link TeamChemistryCalculator};
 * this layer reacts to tactical drawing, so moving a player a few pixels or
 * changing a role can alter chemistry without persisting anything.
 */
public final class TacticalChemistryCalculator {

    private TacticalChemistryCalculator() {
    }

    public static TacticalChemistry calculate(
            List<LineupSlotDTO> slots,
            Map<String, String> naturalByPlayer,
            Map<String, double[]> coordsBySubdivision
    ) {
        if (slots == null || slots.isEmpty()) {
            return empty();
        }
        List<Node> nodes = new ArrayList<>();
        for (LineupSlotDTO slot : slots) {
            if (slot == null || slot.playerId() == null || slot.subdivisionId() == null) {
                continue;
            }
            double[] canonical = coordsBySubdivision == null ? null : coordsBySubdivision.get(slot.subdivisionId());
            double x = slot.customXPercent() != null
                    ? slot.customXPercent()
                    : canonical != null ? canonical[0] : Double.NaN;
            double y = slot.customYPercent() != null
                    ? slot.customYPercent()
                    : canonical != null ? canonical[1] : Double.NaN;
            if (Double.isNaN(x) || Double.isNaN(y)) {
                continue;
            }
            String category = FormationInferer.categoryFor(slot.subdivisionId());
            String natural = naturalByPlayer == null ? null : naturalByPlayer.get(slot.playerId());
            nodes.add(new Node(slot.playerId(), category, natural, x, y));
        }
        if (nodes.size() < 7) {
            return empty();
        }

        List<TacticalChemistry.Link> links = computeLinks(nodes);
        Map<String, Integer> lineScores = lineScores(nodes, links);
        Map<String, Integer> channelScores = channelScores(nodes);
        int score = aggregateScore(links, lineScores, channelScores);
        List<String> warnings = warnings(lineScores, channelScores, links);

        return new TacticalChemistry(score, lineScores, channelScores, links, warnings);
    }

    private static TacticalChemistry empty() {
        Map<String, Integer> lines = new LinkedHashMap<>();
        lines.put("DEF", 0);
        lines.put("MID", 0);
        lines.put("ATT", 0);
        Map<String, Integer> channels = new LinkedHashMap<>();
        channels.put("LEFT", 0);
        channels.put("CENTER", 0);
        channels.put("RIGHT", 0);
        return new TacticalChemistry(0, lines, channels, List.of(), List.of());
    }

    private static List<TacticalChemistry.Link> computeLinks(List<Node> nodes) {
        List<TacticalChemistry.Link> links = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                LinkRule rule = ruleFor(a.category(), b.category());
                if (rule == null) {
                    continue;
                }
                double distance = distance(a, b);
                if (distance > rule.maxDistance()) {
                    continue;
                }
                int score = linkScore(a, b, distance, rule);
                links.add(new TacticalChemistry.Link(
                        a.playerId(),
                        b.playerId(),
                        rule.type(),
                        score,
                        round1(distance),
                        noteFor(score, rule.type())));
            }
        }
        links.sort(Comparator.comparing(TacticalChemistry.Link::score).reversed());
        return List.copyOf(links);
    }

    private static LinkRule ruleFor(String c1, String c2) {
        String a = c1 == null ? "" : c1;
        String b = c2 == null ? "" : c2;
        if (a.compareTo(b) > 0) {
            String tmp = a;
            a = b;
            b = tmp;
        }
        if (a.equals("GK") && b.equals("DEF")) return new LinkRule("GK-DEF", 18, 34);
        if (a.equals("DEF") && b.equals("DEF")) return new LinkRule("DEF-DEF", 22, 32);
        if (a.equals("DEF") && b.equals("MID")) return new LinkRule("DEF-MID", 26, 38);
        if (a.equals("MID") && b.equals("MID")) return new LinkRule("MID-MID", 22, 34);
        if (a.equals("ATT") && b.equals("MID")) return new LinkRule("MID-ATT", 26, 40);
        if (a.equals("ATT") && b.equals("ATT")) return new LinkRule("ATT-ATT", 22, 34);
        return null;
    }

    private static int linkScore(Node a, Node b, double distance, LinkRule rule) {
        double distancePenalty = Math.abs(distance - rule.targetDistance()) * 2.2;
        double rolePenalty = rolePenalty(a) + rolePenalty(b);
        double channelBonus = sameChannel(a, b) ? 4.0 : 0.0;
        return clamp((int) Math.round(100.0 - distancePenalty - rolePenalty + channelBonus));
    }

    private static double rolePenalty(Node n) {
        if (n.natural() == null || n.category() == null) {
            return 4.0;
        }
        String natural = n.natural().toUpperCase();
        String category = n.category();
        if (category.equals("GK")) return natural.equals("GK") ? 0 : 30;
        if (category.equals("DEF")) return natural.equals("DEF") ? 0 : natural.equals("MID") ? 7 : 14;
        if (category.equals("MID")) return natural.equals("MID") || natural.equals("WINGER") ? 0 : 8;
        if (category.equals("ATT")) return natural.equals("ATT") || natural.equals("WINGER") ? 0 : 10;
        return 5.0;
    }

    private static Map<String, Integer> lineScores(List<Node> nodes, List<TacticalChemistry.Link> links) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("DEF", averageLinksForLine("DEF", links));
        result.put("MID", averageLinksForLine("MID", links));
        result.put("ATT", averageLinksForLine("ATT", links));
        for (String line : List.of("DEF", "MID", "ATT")) {
            if (nodes.stream().noneMatch(n -> line.equals(n.category()))) {
                result.put(line, 0);
            }
        }
        return result;
    }

    private static int averageLinksForLine(String line, List<TacticalChemistry.Link> links) {
        List<TacticalChemistry.Link> filtered = links.stream()
                .filter(l -> l.type().contains(line))
                .toList();
        if (filtered.isEmpty()) {
            return 55;
        }
        return clamp((int) Math.round(filtered.stream().mapToInt(TacticalChemistry.Link::score).average().orElse(55)));
    }

    private static Map<String, Integer> channelScores(List<Node> nodes) {
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("LEFT", channelScore(nodes, 0, 36));
        result.put("CENTER", channelScore(nodes, 30, 70));
        result.put("RIGHT", channelScore(nodes, 64, 100));
        return result;
    }

    private static int channelScore(List<Node> nodes, double minX, double maxX) {
        long def = nodes.stream().filter(n -> "DEF".equals(n.category()) && n.x() >= minX && n.x() <= maxX).count();
        long mid = nodes.stream().filter(n -> "MID".equals(n.category()) && n.x() >= minX && n.x() <= maxX).count();
        long att = nodes.stream().filter(n -> "ATT".equals(n.category()) && n.x() >= minX && n.x() <= maxX).count();
        int score = 45;
        if (def > 0) score += 15;
        if (mid > 0) score += 20;
        if (att > 0) score += 15;
        if (def > 0 && mid > 0 && att > 0) score += 5;
        return clamp(score);
    }

    private static int aggregateScore(
            List<TacticalChemistry.Link> links,
            Map<String, Integer> lineScores,
            Map<String, Integer> channelScores
    ) {
        double linkAvg = links.isEmpty()
                ? 55.0
                : links.stream().mapToInt(TacticalChemistry.Link::score).average().orElse(55);
        double lineAvg = lineScores.values().stream().mapToInt(Integer::intValue).average().orElse(55);
        double channelAvg = channelScores.values().stream().mapToInt(Integer::intValue).average().orElse(55);
        return clamp((int) Math.round(linkAvg * 0.55 + lineAvg * 0.25 + channelAvg * 0.20));
    }

    private static List<String> warnings(
            Map<String, Integer> lineScores,
            Map<String, Integer> channelScores,
            List<TacticalChemistry.Link> links
    ) {
        List<String> warnings = new ArrayList<>();
        lineScores.forEach((line, score) -> {
            if (score < 60) warnings.add("Linea " + line + " desconectada");
        });
        channelScores.forEach((channel, score) -> {
            if (score < 65) warnings.add("Canal " + channel + " vulnerable");
        });
        long weakLinks = links.stream().filter(l -> l.score() < 55).count();
        if (weakLinks > 0) {
            warnings.add(weakLinks + " vinculos tacticos debiles");
        }
        return List.copyOf(warnings);
    }

    private static boolean sameChannel(Node a, Node b) {
        return channel(a.x()).equals(channel(b.x()));
    }

    private static String channel(double x) {
        if (x < 36) return "LEFT";
        if (x > 64) return "RIGHT";
        return "CENTER";
    }

    private static double distance(Node a, Node b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(99, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static String noteFor(int score, String type) {
        if (score >= 80) return type + " fuerte";
        if (score >= 65) return type + " estable";
        if (score >= 50) return type + " mejorable";
        return type + " desconectado";
    }

    private record Node(String playerId, String category, String natural, double x, double y) {}

    private record LinkRule(String type, double targetDistance, double maxDistance) {}
}
