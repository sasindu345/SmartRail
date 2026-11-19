package com.smartrail.testsupport;

import com.smartrail.common.config.NetworkConfig;

import java.nio.file.Path;
import java.util.Properties;

/** Test helpers for configuration. Prefer {@link #withOverrides} over editing smartrail.properties. */
public final class TestConfig {

    private TestConfig() {
    }

    /** The real config/smartrail.properties. */
    public static NetworkConfig defaults() {
        return NetworkConfig.load(Path.of(NetworkConfig.DEFAULT_CONFIG_PATH));
    }

    /**
     * The real config plus overrides, e.g. {@code withOverrides("nio.tcp.port", "0")} to let the OS pick a free port
     * in tests. Arguments are key/value pairs.
     */
    public static NetworkConfig withOverrides(String... keyValues) {
        Properties p = new Properties();
        try (var reader = java.nio.file.Files.newBufferedReader(Path.of(NetworkConfig.DEFAULT_CONFIG_PATH))) {
            p.load(reader);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            p.setProperty(keyValues[i], keyValues[i + 1]);
        }
        return NetworkConfig.fromProperties(p);
    }
}
