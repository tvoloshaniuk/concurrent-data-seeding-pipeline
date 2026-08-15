package ua.shpp.pipeline;

import org.junit.jupiter.api.Test;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.exceptions.RowCountMismatchException;
import ua.shpp.validation.DtoValidator;
import ua.shpp.utils.CatalogDimensions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class ProducerConsumerPipelineTest {
    /* static final rather than @BeforeEach because these never change: @BeforeEach exists to
    rebuild state that must be fresh per test, and an int constant has nothing to rebuild. What
    genuinely has to be fresh - the mock and the validator below - already is, since JUnit builds
    a new instance of this class for every test method. */
    private static final int SHOP_COUNT = 3;
    private static final int ITEM_CATALOG_SIZE = 10;
    private static final int PLANNED_ROWS = SHOP_COUNT * ITEM_CATALOG_SIZE;
    private static final CatalogDimensions DIMENSIONS = new CatalogDimensions(SHOP_COUNT, ITEM_CATALOG_SIZE);

    private final DbRepository dbRepository = mock(DbRepository.class);
    private final DtoValidator validator = new DtoValidator();

    @Test
    void execute_completesWhenEveryPlannedRowIsInserted() {
        insertsEverythingItIsGiven();
        ProducerConsumerPipeline pipeline = new ProducerConsumerPipeline();
        AppConfig config = config(PLANNED_ROWS);

        assertDoesNotThrow(() -> pipeline.execute(dbRepository, validator, config, DIMENSIONS));
    }

    /* Producers and consumers run on real pools here, so this also covers the shutdown
    handshake: without poison pills arriving after the producers finish, execute() would
    never return and the test would hang instead of passing. */
    @Test
    void execute_deliversEveryGeneratedRowToTheRepository() throws Exception {
        insertsEverythingItIsGiven();

        new ProducerConsumerPipeline().execute(dbRepository, validator, config(PLANNED_ROWS), DIMENSIONS);

        long rowsSeen = mockingDetails(dbRepository).getInvocations().stream()
                .filter(invocation -> "batchInsertShopEntries".equals(invocation.getMethod().getName()))
                .mapToLong(invocation -> ((List<?>) invocation.getArgument(0)).size())
                .sum();
        assertEquals(PLANNED_ROWS, rowsSeen);
    }

    @Test
    void execute_throwsWhenFewerRowsLandThanTheTargetRequires() {
        when(dbRepository.batchInsertShopEntries(anyList())).thenReturn(0);
        ProducerConsumerPipeline pipeline = new ProducerConsumerPipeline();
        AppConfig config = config(PLANNED_ROWS);

        assertThrows(RowCountMismatchException.class,
                () -> pipeline.execute(dbRepository, validator, config, DIMENSIONS));
    }

    /* Batch failures are tolerated one by one, but the shortfall they cause must still fail
    the run - that split is the whole point of verifyTargetReached living outside the consumer. */
    @Test
    void execute_throwsWhenLostBatchesPushTheTotalBelowTarget() {
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenThrow(new RuntimeException("connection reset"))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        ProducerConsumerPipeline pipeline = new ProducerConsumerPipeline();
        AppConfig config = config(PLANNED_ROWS);

        assertThrows(RowCountMismatchException.class,
                () -> pipeline.execute(dbRepository, validator, config, DIMENSIONS));
    }

    /* Overshooting the target is the normal case, not an edge one: itemCatalogSize is a
    ceilDiv, so a real 3,000,000-row target over 57 shops plans 3,000,024 rows. The exact
    margin is irrelevant, hence an arbitrary 5 - what matters is that a surplus passes. */
    @Test
    void execute_acceptsATargetLowerThanWhatWasPlanned() {
        insertsEverythingItIsGiven();
        ProducerConsumerPipeline pipeline = new ProducerConsumerPipeline();
        AppConfig config = config(PLANNED_ROWS - 5);

        assertDoesNotThrow(() -> pipeline.execute(dbRepository, validator, config, DIMENSIONS));
    }

    /* thenAnswer instead of thenReturn because the value has to depend on the input: invocation
    is the recorded call Mockito hands the lambda, so getArgument(0) is the very list the pipeline
    passed in and the mock reports back exactly as many rows as it was given. thenReturn cannot do
    that - it is fixed before the call happens. */
    private void insertsEverythingItIsGiven() {
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
    }

    private static AppConfig config(int shopEntryTarget) {
        // Credentials only have to be non-blank - the repository is mocked, nothing connects.
        return new AppConfig("jdbc:unused-by-unit-test", "unused", "unused",
                100, 2, 2, 4, shopEntryTarget, 2, 500, 0, true, "Плитка 1");
    }
}
