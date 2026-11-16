package com.unicompanion.learning.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.function.Supplier;

/**
 * Thin helpers so domain services can record Mid-senior style spans/metrics
 * without scattering Observation boilerplate.
 */
public final class ObservabilitySupport {

    private ObservabilitySupport() {
    }

    public static void observe(ObservationRegistry registry, String name, Runnable action) {
        Observation.createNotStarted(name, registry).observe(action);
    }

    public static <T> T observe(ObservationRegistry registry, String name, Supplier<T> action) {
        return Observation.createNotStarted(name, registry).observe(action);
    }

    public static Observation start(ObservationRegistry registry, String name) {
        return Observation.start(name, registry);
    }
}
