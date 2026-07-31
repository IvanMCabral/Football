package com.footballmanager.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.footballmanager.adapters.in.web.career.controllers.CareerAdminController;
import com.footballmanager.adapters.in.web.career.controllers.CareerDebugController;
import com.footballmanager.adapters.in.web.world.AdminWorldController;
import com.footballmanager.adapters.in.web.world.LaLigaSeedController;
import com.footballmanager.adapters.in.web.world.WorldSeedController;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

class ProductionEndpointProfileTest {

    @Test
    void debugAdminHarnessAndSeedControllersAreNotLoadedInProd() {
        assertExcludesProd(AdminWorldController.class);
        assertExcludesProd(CareerAdminController.class);
        assertExcludesProd(WorldSeedController.class);
        assertExcludesProd(LaLigaSeedController.class);
        assertExcludesProd("com.footballmanager.adapters.in.web.testharness.TestHarnessController");
        assertExcludesProd("com.footballmanager.adapters.in.web.testharness.TestHarnessLabsController");
        assertExcludesProd(CareerDebugController.class);
    }

    private static void assertExcludesProd(String controllerClassName) {
        try {
            assertExcludesProd(Class.forName(controllerClassName));
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Controller class not found: " + controllerClassName, e);
        }
    }

    private static void assertExcludesProd(Class<?> controllerClass) {
        Profile profile = controllerClass.getAnnotation(Profile.class);
        assertTrue(profile != null, controllerClass.getName() + " must declare a profile guard");
        String joined = String.join(",", profile.value());
        assertTrue(
            joined.contains("!prod")
                || joined.contains("dev")
                || joined.contains("local")
                || joined.contains("test"),
            controllerClass.getName() + " must not be available in prod");
    }
}
