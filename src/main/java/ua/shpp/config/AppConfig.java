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
        int shopEntryTarget,
        int typeIncreaseCoefficient,
        int maxStockQuantity,
        int invalidRatePercent,
        boolean recreateSchema,
        // Last on purpose: everything above mirrors config.properties in file order, this one
        // alone comes from args[0], so keeping it out of that block makes the two easy to compare.
        String itemType
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
        requirePositive(shopEntryTarget, "shopEntryTarget");
        requirePositive(typeIncreaseCoefficient, "typeIncreaseCoefficient");
        requirePositive(maxStockQuantity, "maxStockQuantity");
        // Not requirePositive: 0 is the normal production setting, meaning "corrupt nothing".
        requireInRange(invalidRatePercent, 0, 100, "invalidRatePercent");
        requireNotBlank(itemType, "itemType");
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
                requiredInt(properties, "shop.entry.target"),
                requiredInt(properties, "type.increase.coefficient"),
                requiredInt(properties, "max.stock.quantity"),
                requiredInt(properties, "invalid.rate.percent"),
                requiredBoolean(properties, "recreate.schema"),
                requiredItemType(args)
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

    // AppConfig should contain not only the configuration file values but also 1 value from String[] args
    private static String requiredItemType(String[] args) {
        if (args == null || args.length == 0) {
            throw new IllegalArgumentException("Missing argument: itemType");
        }
        return args[0];
    }

    // Guarantees that such property exists before writing it into AppConfig record field
    private static String requiredString(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }

    // Based on requiredString() to separate "missing key" a "wrong value" problems
    private static int requiredInt(Properties properties, String key) {
        try {
            return Integer.parseInt(requiredString(properties, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer property: " + key, e);
        }
    }

    /**
     * Rejects anything but "true"/"false" instead of using Boolean.parseBoolean, which silently
     * turns a typo like "yes" into false - exactly the sort of quiet wrong answer this config is
     * meant to prevent.
     */
    private static boolean requiredBoolean(Properties properties, String key) {
        String value = requiredString(properties, key);
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new IllegalArgumentException("Invalid boolean property: " + key + " = " + value);
        }
        return Boolean.parseBoolean(value);
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

    private static void requireInRange(int value, int min, int max, String name) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be within [" + min + ", " + max + "]");
        }
    }
}
