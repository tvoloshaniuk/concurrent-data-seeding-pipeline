package ua.shpp.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvColumnReader {
    // Immutable and input-independent, so it is defined once, not per call.
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .get();

    private CsvColumnReader() {
    }

    // Reads the first column of every data row; header row is skipped and quotes are
    // un-escaped by CSVFormat, not by manual string manipulation.
    public static List<String> readSingleColumn(InputStream stream) {
        try (
                Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                CSVParser parser = CSV_FORMAT.parse(reader)
        ) {
            return parser.stream()
                    .map(csvRecord -> csvRecord.get(0))
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
