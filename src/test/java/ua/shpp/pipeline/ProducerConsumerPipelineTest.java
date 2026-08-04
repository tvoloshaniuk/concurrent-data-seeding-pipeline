package ua.shpp.pipeline;

import org.junit.jupiter.api.Test;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.exceptions.RowCountMismatchException;
import ua.shpp.hibernateValidator.DtoValidator;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class ProducerConsumerPipelineTest {
    private static final int SHOP_COUNT = 3;
    private static final int ITEM_CATALOG_SIZE = 10;
    private static final int PLANNED_ROWS = SHOP_COUNT * ITEM_CATALOG_SIZE;

    private final DbRepository dbRepository = mock(DbRepository.class);
    private final DtoValidator validator = new DtoValidator();

    @Test
    void execute_completesWhenEveryPlannedRowIsInserted() {
        insertsEverythingItIsGiven();

        assertDoesNotThrow(() -> new ProducerConsumerPipeline()
                .execute(dbRepository, validator, config(PLANNED_ROWS), SHOP_COUNT, ITEM_CATALOG_SIZE)); //todo /stepwise-explanation needed
    }

    /* Producers and consumers run on real pools here, so this also covers the shutdown
    handshake: without poison pills arriving after the producers finish, execute() would
    never return and the test would hang instead of passing. */
    @Test
    void execute_deliversEveryGeneratedRowToTheRepository() throws Exception {
        insertsEverythingItIsGiven();

        new ProducerConsumerPipeline().execute(dbRepository, validator, config(PLANNED_ROWS), SHOP_COUNT, ITEM_CATALOG_SIZE);

        long rowsSeen = mockingDetails(dbRepository).getInvocations().stream()
                .filter(invocation -> "batchInsertShopEntries".equals(invocation.getMethod().getName()))
                .mapToLong(invocation -> ((List<?>) invocation.getArgument(0)).size())
                .sum();
        assertEquals(PLANNED_ROWS, rowsSeen);
    }

    @Test
    void execute_throwsWhenFewerRowsLandThanTheTargetRequires() {
        when(dbRepository.batchInsertShopEntries(anyList())).thenReturn(0);

        assertThrows(RowCountMismatchException.class, () -> new ProducerConsumerPipeline()
                .execute(dbRepository, validator, config(PLANNED_ROWS), SHOP_COUNT, ITEM_CATALOG_SIZE));
    }

    /* Batch failures are tolerated one by one, but the shortfall they cause must still fail
    the run - that split is the whole point of verifyTargetReached living outside the consumer. */
    @Test
    void execute_throwsWhenLostBatchesPushTheTotalBelowTarget() {
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenThrow(new RuntimeException("connection reset"))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        assertThrows(RowCountMismatchException.class, () -> new ProducerConsumerPipeline()
                .execute(dbRepository, validator, config(PLANNED_ROWS), SHOP_COUNT, ITEM_CATALOG_SIZE));
    }

    @Test
    void execute_acceptsATargetLowerThanWhatWasPlanned() {
        insertsEverythingItIsGiven();

        assertDoesNotThrow(() -> new ProducerConsumerPipeline()
                .execute(dbRepository, validator, config(PLANNED_ROWS - 5), SHOP_COUNT, ITEM_CATALOG_SIZE));
    }

    private void insertsEverythingItIsGiven() {
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
    }

    private static AppConfig config(int shopEntryTarget) {
        // Credentials only have to be non-blank - the repository is mocked, nothing connects.
        return new AppConfig("jdbc:unused-by-unit-test", "unused", "unused",
                100, 2, 2, 4, "Плитка 1", shopEntryTarget, 2, 500);
    }
}
