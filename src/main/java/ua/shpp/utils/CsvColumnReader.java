package ua.shpp.utils;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvColumnReader {
    /**
     * Immutable and input-independent, so it is defined once, not per call.
     * <p>
     * setCommentMarker is not on by default - without it a '#' line is read as ordinary data, so
     * it has to be asked for. It earns its place now that both CSVs carry deliberately invalid
     * rows: without a comment explaining them, the next reader takes them for typos and "fixes"
     * them, quietly disabling the validation they exist to exercise.
     */
    private static final CSVFormat CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setCommentMarker('#')
            .get();

    private CsvColumnReader() {
    }

    /**
     * Reads the first column of every data row; header row and '#' comment lines are skipped and
     * quotes are un-escaped by CSVFormat, not by manual string manipulation.
     * <p>
     * Structurally broken input (an unclosed quote, say) makes the parser throw IOException, which
     * becomes the RuntimeException below - a malformed source file stops the run rather than
     * yielding half-read data.
     */
    public static List<String> readFirstColumn(InputStream stream) {
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
