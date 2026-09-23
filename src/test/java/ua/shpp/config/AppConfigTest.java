package ua.shpp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.shpp.utils.ResourceLoader;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {
    /* happy-path */
    /* 1) constructor */
    /*      - basic */
    @Test
    void constructor_acceptsValues_whenAllFieldsValid() {
        assertDoesNotThrow(
                () -> new AppConfig(
                        "jdbc:postgresql://test-host:5432/test-db",
                        "test-user",
                        "test-password",
                        11,
                        3,
                        5,
                        7,
                        13,
                        2,
                        500,
                        19,
                        true,
                        true,
                        "TestItemType 1"
                )
        );
    }

    /*      - edge cases */
    @ParameterizedTest
    @CsvSource({
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 1, true, true, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 1, false, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 99, false, true, 'TestItemType'",

    })
    void constructor_acceptsValues_whenPositiveFieldAtMinimum(
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
            boolean recreateIndexes,
            String itemType
    ) {
        assertDoesNotThrow(
                () -> new AppConfig(
                        dbUrl,
                        dbUser,
                        dbPassword,
                        queueCapacity,
                        producerThreadPoolSize,
                        consumerThreadPoolSize,
                        batchSize,
                        shopEntryTarget,
                        typeIncreaseCoefficient,
                        maxStockQuantity,
                        invalidRatePercent,
                        recreateSchema,
                        recreateIndexes,
                        itemType
                )
        );
    }

    /* 2) load */
    /*      - basic */
    @Test
    void load_returnsExpectedConfig_whenValidPropertiesAndArgs() {
        String[] args = {"TestItemType 1"};
        AppConfig config = AppConfig.load(args);

        assertEquals(config.dbUrl(), "jdbc:postgresql://test-host:5432/test-db");
        assertEquals(config.dbUser(), "test-user");
        assertEquals(config.dbPassword(), "test-password");
        assertEquals(config.queueCapacity(), 11);
        assertEquals(config.producerThreadPoolSize(), 3);
        assertEquals(config.consumerThreadPoolSize(), 5);
        assertEquals(config.batchSize(), 7);
        assertEquals(config.shopEntryTarget(), 13);
        assertEquals(config.typeIncreaseCoefficient(), 2);
        assertEquals(config.maxStockQuantity(), 17);
        assertEquals(config.invalidRatePercent(), 19);
        assertFalse(config.recreateSchema());
        assertTrue(config.recreateIndexes());
        assertEquals(config.itemType(), "TestItemType 1");
    }

    @ParameterizedTest
    @CsvSource({
            "TrUe, TRUE, true",
            "FALSE, fAlSe , false"
    })
    void load_parsesBooleanPropertiesIgnoringCase(String recreateSchema, String recreateIndexes, boolean expectedValue) {
        String[] args = {"TestItemType 1"};
        Properties properties = ResourceLoader.readProperties("config.properties");
        properties.setProperty("recreate.schema", recreateSchema);
        properties.setProperty("recreate.indexes", recreateIndexes);
        AppConfig config = AppConfig.load(properties, args);
        assertEquals(config.recreateSchema(), expectedValue);
        assertEquals(config.recreateIndexes(), expectedValue);

    }

    /* negative-path */
    /*  1) constructor */
    /*      - basic */
    @ParameterizedTest
    @CsvSource({
            ", test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 1, true, false, 'TestItemType'",
            "' ', test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 1, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 0, 1, 1, 1, 1, 1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 0, 1, 1, 1, 1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 0, 1, 1, 1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 0, 1, 1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, -1, 1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, -1, 1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, -1, 0, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 100, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 101, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, -1, true, false, 'TestItemType'",
            "jdbc:postgresql://test-host:5432/test-db, test-user, test-password, 1, 1, 1, 1, 1, 1, 1, 0, true, false, ''"
    })
    void constructor_throws_whenFieldsInvalid(
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
            boolean recreateIndexes,
            String itemType
    ) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppConfig(
                        dbUrl,
                        dbUser,
                        dbPassword,
                        queueCapacity,
                        producerThreadPoolSize,
                        consumerThreadPoolSize,
                        batchSize,
                        shopEntryTarget,
                        typeIncreaseCoefficient,
                        maxStockQuantity,
                        invalidRatePercent,
                        recreateSchema,
                        recreateIndexes,
                        itemType
                )
        );
    }

    /*  2) load */
    @ParameterizedTest
    @ValueSource(strings = {
            "db.url",
            "db.user",
            "db.password",
            "queue.capacity",
            "producer.thread.pool.size",
            "consumer.thread.pool.size",
            "batch.size",
            "shop.entry.target",
            "type.increase.coefficient",
            "max.stock.quantity",
            "invalid.rate.percent",
            "recreate.schema",
            "recreate.indexes"
    })
    //not found in properties file (and Properties return null for getProperty(key))
    void load_throws_whenRequiredPropertyMissing(String missingKey) {
        String[] args = {"TestItemType 1"};
        Properties properties = ResourceLoader.readProperties("config.properties");
        properties.remove(missingKey);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> AppConfig.load(properties, args));
        assertTrue(exception.getMessage().contains("Missing property: " + missingKey));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "db.url",
            "db.user",
            "db.password",
            "queue.capacity",
            "producer.thread.pool.size",
            "consumer.thread.pool.size",
            "batch.size",
            "shop.entry.target",
            "type.increase.coefficient",
            "max.stock.quantity",
            "invalid.rate.percent",
            "recreate.schema",
            "recreate.indexes"
    })
    void load_throws_whenPropertyIsBlank(String propertyKey) {
        String[] args = {"TestItemType 1"};
        Properties properties = ResourceLoader.readProperties("config.properties");
        properties.setProperty(propertyKey, "");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> AppConfig.load(properties, args));
        assertTrue(exception.getMessage().contains("Missing property: " + propertyKey));

        properties.setProperty(propertyKey, " ");
        exception = assertThrows(
                IllegalArgumentException.class, () -> AppConfig.load(properties, args));
        assertTrue(exception.getMessage().contains("Missing property: " + propertyKey));
    }



}