package ua.shpp.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppConfigTest {
    private static final String VALID_URL = "jdbc:postgresql://localhost:5432/epicenter";


    @Test
    void load_takesItemTypeFromFirstArgAndRestFromClasspath() {
        AppConfig config = AppConfig.load(new String[]{"Сантехніка 1"});

        assertEquals("Сантехніка 1", config.itemType());
        assertEquals("jdbc:postgresql://test-host:5432/test-db", config.dbUrl());
        assertEquals(7, config.batchSize());
        assertEquals(19, config.invalidRatePercent());
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
        assertThrows(IllegalArgumentException.class, () -> configWith(VALID_URL, 0, 500));
    }

    @Test
    void constructor_throwsForNonPositiveMaxStockQuantity() {
        assertThrows(IllegalArgumentException.class, () -> configWith(VALID_URL, 500, 0));
    }

    @Test
    void constructor_acceptsZeroInvalidRate() {
        assertDoesNotThrow(() -> configWithInvalidRate(0));
    }

    @Test
    void constructor_throwsForInvalidRateAbove100() {
        assertThrows(IllegalArgumentException.class, () -> configWithInvalidRate(101));
    }

    @Test
    void constructor_throwsForNegativeInvalidRate() {
        assertThrows(IllegalArgumentException.class, () -> configWithInvalidRate(-1));
    }

    /* The counterpart to the three above: they prove the validation rejects bad input, this one
    proves it is not so strict that it rejects good input. Asserting the getters instead would
    only be testing that a record returns what it was given. */
    /* Kept last on purpose: the rejection tests above define what "invalid" means, so this one
    reads as the closing statement that nothing valid got caught in that net. */
    @Test
    void constructor_acceptsFullyPopulatedConfig() {
        assertDoesNotThrow(() -> configWith(VALID_URL, 500, 500));
    }

    private static AppConfig configWithInvalidRate(int invalidRatePercent) {
        return new AppConfig(VALID_URL, "postgres", "123", 5000, 2, 4, 500,
                3_000_000, 1000, 500, invalidRatePercent, true, "Сантехніка 1");
    }

    private static AppConfig configWith(String dbUrl, int batchSize, int maxStockQuantity) {
        return new AppConfig(dbUrl, "postgres", "123", 5000, 2, 4, batchSize,
                3_000_000, 1000, maxStockQuantity, 0, true, "Сантехніка 1");
    }
}
