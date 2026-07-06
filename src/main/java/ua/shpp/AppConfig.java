package ua.shpp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        int queueCapacity,
        int threadPoolSize,
        int batchSize,
        String itemType
) {
    public AppConfig {
        requireNotBlank(dbUrl, "dbUrl");
        requireNotBlank(dbUser, "dbUser");
        requireNotBlank(dbPassword, "dbPassword");
        requirePositive(queueCapacity, "queueCapacity");
        requirePositive(threadPoolSize, "threadPoolSize");
        requirePositive(batchSize, "batchSize");
        requireNotBlank(itemType, "itemType");
    }

    public static AppConfig load() {
        Properties properties = loadProperties();
        return new AppConfig(
                requiredString(properties, "db.url"),
                requiredString(properties, "db.user"),
                requiredString(properties, "db.password"),
                requiredInt(properties, "queue.capacity"),
                requiredInt(properties, "thread.pool.size"),
                requiredInt(properties, "batch.size"),
                System.getProperty("itemType")
        );
    }

    private static Properties loadProperties() {
        try (InputStreamReader reader = new InputStreamReader(
                ResourceLoader.stream("config.properties"),
                StandardCharsets.UTF_8
        )) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    private static String requiredString(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }

    private static int requiredInt(Properties properties, String key) {
        try {
            return Integer.parseInt(requiredString(properties, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer property: " + key, e);
        }
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
