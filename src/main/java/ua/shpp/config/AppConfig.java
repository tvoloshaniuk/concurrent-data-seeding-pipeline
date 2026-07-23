package ua.shpp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.utils.ResourceLoader;

import java.util.Properties;

public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        int queueCapacity,
        int producerThreadPoolSize,
        int consumerThreadPoolSize,
        int batchSize,
        String itemType,
        int shopEntryTarget,
        int typeIncreaseCoefficient,
        int maxStockQuantity
) {
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public AppConfig {
        requireNotBlank(dbUrl, "dbUrl");
        requireNotBlank(dbUser, "dbUser");
        requireNotBlank(dbPassword, "dbPassword");
        requirePositive(queueCapacity, "queueCapacity");
        requirePositive(producerThreadPoolSize, "producerThreadPoolSize");
        requirePositive(consumerThreadPoolSize, "consumerThreadPoolSize");
        requirePositive(batchSize, "batchSize");
        requireNotBlank(itemType, "itemType");
        requirePositive(shopEntryTarget, "shopEntryTarget");
        requirePositive(typeIncreaseCoefficient, "typeIncreaseCoefficient");
        requirePositive(maxStockQuantity, "maxStockQuantity");
    }

    public static AppConfig load(String[] args) {
        Properties properties = ResourceLoader.readProperties("config.properties");
        AppConfig config = new AppConfig(
                requiredString(properties, "db.url"),
                requiredString(properties, "db.user"),
                requiredString(properties, "db.password"),
                requiredInt(properties, "queue.capacity"),
                requiredInt(properties, "producer.thread.pool.size"),
                requiredInt(properties, "consumer.thread.pool.size"),
                requiredInt(properties, "batch.size"),
                requiredItemType(args),
                requiredInt(properties, "shop.entry.target"),
                requiredInt(properties, "type.increase.coefficient"),
                requiredInt(properties, "max.stock.quantity")
        );
        log.info(
                "Loaded config: dbUrl={}, dbUser={}, queueCapacity={}, producerThreadPoolSize={}, "
                        + "consumerThreadPoolSize={}, batchSize={}, itemType={}, shopEntryTarget={}, "
                        + "typeIncreaseCoefficient={}, maxStockQuantity={}",
                config.dbUrl(),
                config.dbUser(),
                config.queueCapacity(),
                config.producerThreadPoolSize(),
                config.consumerThreadPoolSize(),
                config.batchSize(),
                config.itemType(),
                config.shopEntryTarget(),
                config.typeIncreaseCoefficient(),
                config.maxStockQuantity()
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
