package ua.shpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public AppConfig {
        requireNotBlank(dbUrl, "dbUrl");
        requireNotBlank(dbUser, "dbUser");
        requireNotBlank(dbPassword, "dbPassword");
        requirePositive(queueCapacity, "queueCapacity");
        requirePositive(threadPoolSize, "threadPoolSize");
        requirePositive(batchSize, "batchSize");
        requireNotBlank(itemType, "itemType");
    }

    public static AppConfig load(String[] args) {
        Properties properties = ResourceLoader.readProperties("config.properties");
        AppConfig config = new AppConfig(
                requiredString(properties, "db.url"),
                requiredString(properties, "db.user"),
                requiredString(properties, "db.password"),
                requiredInt(properties, "queue.capacity"),
                requiredInt(properties, "thread.pool.size"),
                requiredInt(properties, "batch.size"),
                requiredItemType(args)
        );
        log.info(
                "Loaded config: dbUrl={}, dbUser={}, queueCapacity={}, threadPoolSize={}, batchSize={}, itemType={}",
                config.dbUrl(),
                config.dbUser(),
                config.queueCapacity(),
                config.threadPoolSize(),
                config.batchSize(),
                config.itemType()
        );
        return config;
    }

    private static String requiredItemType(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing argument: itemType");
        }
        return args[0];
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
