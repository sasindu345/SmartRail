package com.smartrail.common.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Read-only view of {@code config/smartrail.properties}.
 *
 * <p>Lookup order for every key: JVM system property ({@code -Dkey=value}) first, then the file.
 * The file location itself can be changed with {@code -Dsmartrail.config=path}.
 *
 * <p>SHARED CONTRACT (frozen). Always pass key names from {@link ConfigKeys}.
 */
public final class NetworkConfig {

    /** System property that points to an alternative config file. */
    public static final String CONFIG_PATH_PROPERTY = "smartrail.config";
    /** Default config file, relative to the project root (working directory). */
    public static final String DEFAULT_CONFIG_PATH = "config/smartrail.properties";

    private final Properties properties;

    private NetworkConfig(Properties properties) {
        this.properties = properties;
    }

    /** Loads the default file (or the one named by {@code -Dsmartrail.config}). */
    public static NetworkConfig load() {
        return load(Path.of(System.getProperty(CONFIG_PATH_PROPERTY, DEFAULT_CONFIG_PATH)));
    }

    public static NetworkConfig load(Path path) {
        Properties loaded = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            loaded.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read config file " + path.toAbsolutePath()
                    + " (run from the project root or pass -D" + CONFIG_PATH_PROPERTY + "=...)", e);
        }
        return new NetworkConfig(loaded);
    }

    /** Builds a config from in-memory properties. Intended for unit tests. */
    public static NetworkConfig fromProperties(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return new NetworkConfig(copy);
    }

    /** Required value; throws if the key is missing. */
    public String get(String key) {
        String value = raw(key);
        if (value == null) {
            throw new IllegalStateException("Missing config key: " + key);
        }
        return value;
    }

    /** Optional value; empty when the key is missing or blank. */
    public Optional<String> find(String key) {
        String value = raw(key);
        return value == null || value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    public int getInt(String key) {
        try {
            return Integer.parseInt(get(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Config key " + key + " must be an integer", e);
        }
    }

    public long getLong(String key) {
        try {
            return Long.parseLong(get(key));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Config key " + key + " must be a number", e);
        }
    }

    public boolean getBoolean(String key) {
        return Boolean.parseBoolean(get(key));
    }

    /** Comma-separated list; blank entries are dropped. */
    public List<String> getList(String key) {
        return Arrays.stream(get(key).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public Path getPath(String key) {
        return Path.of(get(key));
    }

    /**
     * Resolves a secret. {@code envNameKey} is a config key whose VALUE is the name of an
     * environment variable (e.g. {@code tls.keystore.password.env=SMARTRAIL_KEYSTORE_PASSWORD}).
     * The environment variable is read first, then a system property with the same name.
     */
    public char[] secret(String envNameKey) {
        String variable = get(envNameKey);
        String value = System.getenv(variable);
        if (value == null) {
            value = System.getProperty(variable);
        }
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("Secret not set: export " + variable + "=... (see docs/configuration.md)");
        }
        return value.toCharArray();
    }

    private String raw(String key) {
        String override = System.getProperty(key);
        String value = override != null ? override : properties.getProperty(key);
        return value == null ? null : value.trim();
    }
}
