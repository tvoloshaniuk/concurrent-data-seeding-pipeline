package ua.shpp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {

    @Test
    void load_takesItemTypeFromFirstArgAndRestFromClasspath() {
        AppConfig config = AppConfig.load(new String[]{"Сантехніка 1"});

        assertEquals("Сантехніка 1", config.itemType());
        assertNotNull(config.dbUrl());
        assertEquals(500, config.batchSize());
    }

    @Test
    void load_throwsWhenItemTypeArgMissing() {
        assertThrows(IllegalArgumentException.class, () -> AppConfig.load(new String[]{}));
    }

    @Test
    void constructor_throwsForBlankDbUrl() {
        assertThrows(IllegalArgumentException.class, () -> configWith("", 500, 500));
    }

    @Test
    void constructor_throwsForNonPositiveBatchSize() {
        assertThrows(IllegalArgumentException.class, () -> configWith(validUrl(), 0, 500));
    }

    /* maxStockQuantity guards the random upper bound in ShopEntryGenerator - a zero would
    silently make every shop stock nothing, so it is rejected rather than tolerated. */
    @Test
    void constructor_throwsForNonPositiveMaxStockQuantity() {
        assertThrows(IllegalArgumentException.class, () -> configWith(validUrl(), 500, 0));
    }

    @Test
    void constructor_acceptsFullyPopulatedConfig() {
        AppConfig config = configWith(validUrl(), 500, 500);

        assertEquals(500, config.batchSize());
        assertEquals(500, config.maxStockQuantity());
    }

    private static String validUrl() {
        return "jdbc:postgresql://localhost:5432/epicenter";
    }

    private static AppConfig configWith(String dbUrl, int batchSize, int maxStockQuantity) {
        return new AppConfig(dbUrl, "postgres", "123", 5000, 2,
                4, batchSize,
                "Сантехніка 1", 3_000_000, 1000, maxStockQuantity);
    }
}
