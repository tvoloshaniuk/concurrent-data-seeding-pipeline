package ua.shpp.db;

import org.junit.jupiter.api.Test;

import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers only countInserted - the one piece of DbRepository that holds logic rather than JDBC
 * plumbing. Everything else opens a Connection and belongs to an integration test.
 */
class DbRepositoryTest {
    private final DbRepository dbRepository = new DbRepository(null);

    @Test
    void countInserted_countsRowsReportedAsSingleInserts() {
        assertEquals(3, dbRepository.countInserted(new int[]{1, 1, 1}));
    }

    /* SUCCESS_NO_INFO is what the driver returns when reWriteBatchedInserts merges several rows
    into one statement: the row landed, the driver just cannot say how many per entry. */
    @Test
    void countInserted_treatsSuccessNoInfoAsInserted() {
        assertEquals(2, dbRepository.countInserted(
                new int[]{Statement.SUCCESS_NO_INFO, Statement.SUCCESS_NO_INFO}));
    }

    // 0 is what ON CONFLICT DO NOTHING reports for a skipped duplicate - not an insert.
    @Test
    void countInserted_ignoresRowsSkippedByOnConflict() {
        assertEquals(1, dbRepository.countInserted(new int[]{1, 0, 0}));
    }

    @Test
    void countInserted_ignoresFailedRows() {
        assertEquals(1, dbRepository.countInserted(new int[]{1, Statement.EXECUTE_FAILED}));
    }

    @Test
    void countInserted_returnsZeroForEmptyBatch() {
        assertEquals(0, dbRepository.countInserted(new int[0]));
    }

    @Test
    void countInserted_mixesExactAndNoInfoResults() {
        assertEquals(3, dbRepository.countInserted(
                new int[]{1, Statement.SUCCESS_NO_INFO, 0, 1, Statement.EXECUTE_FAILED}));
    }
}
