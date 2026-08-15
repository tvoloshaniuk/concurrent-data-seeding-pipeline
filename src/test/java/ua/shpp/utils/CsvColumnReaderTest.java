package ua.shpp.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* Not tests of commons-csv but of the four choices made when configuring it - setHeader,
setSkipHeaderRecord, setTrim and get(0). Flip any of them and the library still works perfectly
while this class starts returning the header row, the wrong column, or padded values. That is
what these pin down. */
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

    /* Quoting is what lets an address hold a comma without splitting into two columns -
    handled by CSVFormat, which is the reason this class exists instead of String.split(","). */
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
