package ua.shpp.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvColumnReaderTest {

    @Test
    void readSingleColumn_skipsHeaderRow() {
        List<String> values = CsvColumnReader.readSingleColumn(csv("name\nПлитка\nПосуд"));

        assertEquals(List.of("Плитка", "Посуд"), values);
    }

    @Test
    void readSingleColumn_ignoresEveryColumnButTheFirst() {
        List<String> values = CsvColumnReader.readSingleColumn(csv("name,note\nПлитка,ignored\nПосуд,also ignored"));

        assertEquals(List.of("Плитка", "Посуд"), values);
    }

    @Test
    void readSingleColumn_trimsSurroundingWhitespace() {
        List<String> values = CsvColumnReader.readSingleColumn(csv("name\n   Плитка   "));

        assertEquals(List.of("Плитка"), values);
    }

    /* Quoting is what lets an address hold a comma without splitting into two columns -
    handled by CSVFormat, which is the reason this class exists instead of String.split(","). */
    @Test
    void readSingleColumn_keepsCommasInsideQuotedValues() {
        List<String> values = CsvColumnReader.readSingleColumn(csv("address\n\"Київ, вул. Берковецька 6К\""));

        assertEquals(List.of("Київ, вул. Берковецька 6К"), values);
    }

    @Test
    void readSingleColumn_returnsEmptyWhenOnlyHeaderPresent() {
        List<String> values = CsvColumnReader.readSingleColumn(csv("name\n"));

        assertTrue(values.isEmpty());
    }

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
