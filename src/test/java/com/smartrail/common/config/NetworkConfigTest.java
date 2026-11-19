package com.smartrail.common.config;

import com.smartrail.testsupport.TestConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkConfigTest {

    @Test
    void everyConfigKeyExistsInTheFile() throws IllegalAccessException {
        NetworkConfig config = TestConfig.defaults();
        for (Field f : ConfigKeys.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                String key = (String) f.get(null);
                assertDoesNotThrow(() -> config.find(key), key);
                assertTrue(hasKey(config, key), "config/smartrail.properties is missing key " + key);
            }
        }
    }

    @Test
    void overridesWin() {
        NetworkConfig config = TestConfig.withOverrides(ConfigKeys.NIO_TCP_PORT, "6123");
        assertEquals(6123, config.getInt(ConfigKeys.NIO_TCP_PORT));
    }

    @Test
    void missingKeyFailsClearly() {
        assertThrows(IllegalStateException.class, () -> TestConfig.defaults().get("no.such.key"));
    }

    private static boolean hasKey(NetworkConfig config, String key) {
        try {
            config.get(key);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }
}
