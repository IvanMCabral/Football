package com.footballmanager.application.service.world.importer;

import java.util.List;

public record ThreeLeagueImportReport(
    int countries,
    int leagues,
    int clubs,
    int teams,
    int players,
    int playerSpecialAttributes,
    List<String> warnings
) {}
