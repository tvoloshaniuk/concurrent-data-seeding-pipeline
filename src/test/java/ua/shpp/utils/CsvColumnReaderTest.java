package ua.shpp.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Tests the commons-csv configuration - setHeader, setSkipHeaderRecord, setTrim, get(0) - not the library.
class CsvColumnReaderTest {

    @Test
    void readFirstColumn_skipsHeaderRow() {
        List<String> values = CsvColumnReader.readFirstColumn(csv("name\nПлитка\nПосуд"));

        assertEquals(List.of("Плитка", "Посуд"), values);
    }

    @Test
    void readFirstColumn_ignoresEveryColumnButTheFirst() {
        List<String> values = CsvColumnReader.readFirstColumn(csv("name,note\nПлитка,ignored\nПосуд,also ignored"));

        assertEquals(List.of("Плитка", "Посуд"), values);
    }

    @Test
    void readFirstColumn_trimsSurroundingWhitespace() {
        List<String> values = CsvColumnReader.readFirstColumn(csv("name\n   Плитка   "));

        assertEquals(List.of("Плитка"), values);
    }

    @Test
    void readFirstColumn_keepsCommasInsideQuotedValues() {
        List<String> values = CsvColumnReader.readFirstColumn(csv("address\n\"Київ, вул. Берковецька 6К\""));

        assertEquals(List.of("Київ, вул. Берковецька 6К"), values);
    }

    @Test
    void readFirstColumn_returnsEmptyWhenOnlyHeaderPresent() {
        List<String> values = CsvColumnReader.readFirstColumn(csv("name\n"));

        assertTrue(values.isEmpty());
    }

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
