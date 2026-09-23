package ua.shpp.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

class FoundationTablesPopulatorTest {
    @Test
    void populate_returnsExpectedCatalogDimensionsAccordingToInputs() {
        String shopsCsv = "address\nКиїв 1\nЛьвів 2\nОдеса 3";
        InputStream shops = new ByteArrayInputStream(
                shopsCsv.getBytes(StandardCharsets.UTF_8));

        FoundationTablesPopulator populator = new FoundationTablesPopulator(null, null);
        populator.populate(shops);
    }
}