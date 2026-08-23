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

    @Test
    void execute_acceptsATargetLowerThanWhatWasPlanned() {
        insertsEverythingItIsGiven();
        ProducerConsumerPipeline pipeline = new ProducerConsumerPipeline();
        AppConfig config = config(PLANNED_ROWS - 5);

        assertDoesNotThrow(() -> pipeline.execute(dbRepository, validator, config, DIMENSIONS));
    }

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
