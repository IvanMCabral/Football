package com.footballmanager.infrastructure.world.importer;

import com.footballmanager.application.service.world.importer.ThreeLeagueImportReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.world.import.three-league", havingValue = "true")
public class ThreeLeagueImportRunner implements ApplicationRunner {

    private final ThreeLeagueDatasetImporter importer;

    @Override
    public void run(ApplicationArguments args) {
        ThreeLeagueImportReport report = importer.importDataset();
        log.info(
            "Three-league dataset import completed countries={} leagues={} clubs={} teams={} players={} traits={}",
            report.countries(), report.leagues(), report.clubs(), report.teams(), report.players(),
            report.playerSpecialAttributes());
    }
}
