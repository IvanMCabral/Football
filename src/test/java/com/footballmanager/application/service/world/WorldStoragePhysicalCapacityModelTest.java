package com.footballmanager.application.service.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStoragePhysicalCapacityModelTest {

    private final WorldStoragePhysicalCapacityModel model = new WorldStoragePhysicalCapacityModel();

    @Test
    void physicalEstimateIsConservativeAndMonotonic() {
        var small = model.estimate(1_000_000, 100_000, 40_000, 30_000, 50_000, false);
        var large = model.estimate(1_000_000, 100_000, 80_000, 60_000, 90_000, false);

        assertTrue(small.preparedPhysicalBytes() > 40_000);
        assertTrue(small.catalogPhysicalBytes() > 50_000);
        assertTrue(large.physicalPeakBytes() > small.physicalPeakBytes());
        assertEquals(0, model.redisStringBytes(0));
        assertThrows(IllegalArgumentException.class, () -> model.redisStringBytes(-1));
    }

    @Test
    void existingCatalogReceivesCreditOnlyForItsWholePhysicalWrite() {
        var absent = model.estimate(1_000_000, 100_000, 40_000, 30_000, 50_000, false);
        var existing = model.estimate(1_000_000, 100_000, 40_000, 30_000, 50_000, true);
        assertEquals(model.redisStringBytes(50_000),
                absent.physicalPeakBytes() - existing.physicalPeakBytes());
    }
}
