package com.footballmanager.infrastructure.world.importer;

import com.footballmanager.application.service.world.importer.ThreeLeagueImportReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThreeLeagueImportRunner implements ApplicationRunner {

    private final ThreeLeagueDatasetImporter importer;

    @Value("${APP_WORLD_IMPORT_THREE_LEAGUE:false}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Three-league dataset import runner enabled={}", enabled);
        if (!enabled) {
            return;
        }
        log.info("Scheduling three-league dataset import in the staging worker");
        CompletableFuture.runAsync(this::executeImport);
    }

    private void executeImport() {
        log.info("Starting three-league dataset import");
        try {
            ThreeLeagueImportReport report = importer.importDataset();
            log.info(
                "Three-league dataset import completed countries={} leagues={} clubs={} teams={} players={} traits={}",
                report.countries(), report.leagues(), report.clubs(), report.teams(), report.players(),
                report.playerSpecialAttributes());
        } catch (RuntimeException exception) {
            log.error("Three-league dataset import failed", exception);
            throw exception;
        }
    }
}
