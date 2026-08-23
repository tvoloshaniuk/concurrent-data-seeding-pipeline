package ua.shpp;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EpicenterSeedingServiceTest {
    private final DbRepository dbRepository = mock(DbRepository.class);

    @Test
    void execute_failsBeforeGeneratingShopEntriesWhenItemTypeIsUnknown() {
        when(dbRepository.existsItemType(anyString())).thenReturn(false);

        EpicenterSeedingService service = service();

        assertThrows(IllegalArgumentException.class, service::execute);

        verify(dbRepository, never()).batchInsertShopEntries(anyList());
    }

    @Test
    void execute_explainsTheSuffixConventionWhenItemTypeIsUnknown() {
        when(dbRepository.existsItemType(anyString())).thenReturn(false);

        EpicenterSeedingService service = service();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, service::execute);

        assertTrue(thrown.getMessage().contains("Сантехніка"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("suffix"), thrown.getMessage());
    }

    @Test
    void execute_failsBeforeGeneratingShopEntriesWhenItemTypeHoldsNoItems() {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(false);

        EpicenterSeedingService service = service();

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, service::execute);

        assertTrue(thrown.getMessage().contains("typeIncreaseCoefficient"), thrown.getMessage());
        verify(dbRepository, never()).batchInsertShopEntries(anyList());
    }

    @Test
    void execute_completesWhenItemTypeExistsAndEveryRowLands() {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(dbRepository.findShopWithMaxItems(anyString())).thenReturn("Київ, вул. Берковецька 6К");

        EpicenterSeedingService service = service();

        assertDoesNotThrow(service::execute);
    }

    @Test
    void execute_toleratesNoMatchingShopWithoutFailingTheRun() {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(dbRepository.findShopWithMaxItems(anyString())).thenReturn(null);

        EpicenterSeedingService service = service();

        assertDoesNotThrow(service::execute);
    }

    @Test
    void execute_buildsSecondaryIndexesOnlyAfterShopEntryIsFilled() throws Exception {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        service().execute();

        InOrder inOrder = inOrder(dbRepository);
        inOrder.verify(dbRepository, atLeastOnce()).batchInsertShopEntries(anyList());
        inOrder.verify(dbRepository).runDdl(contains("CREATE INDEX"));
    }

    @Test
    void execute_skipsGenerationWhenSchemaIsKeptAndDataAlreadyExists() throws Exception {
        when(dbRepository.hasShopEntries()).thenReturn(true);
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(true);

        new EpicenterSeedingService(config(false), dbRepository).execute();

        verify(dbRepository, never()).runDdl(anyString());
        verify(dbRepository, never()).batchInsertShopEntries(anyList());
        verify(dbRepository).findShopWithMaxItems(anyString());
    }

    // An empty table is not a reason to skip - there would be nothing to search afterwards.
    @Test
    void execute_populatesWhenSchemaIsKeptButTableIsEmpty() throws Exception {
        when(dbRepository.hasShopEntries()).thenReturn(false);
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        new EpicenterSeedingService(config(false), dbRepository).execute();

        verify(dbRepository, atLeastOnce()).batchInsertShopEntries(anyList());
    }

    // At recreate.schema=true the table contents are irrelevant - it always rebuilds.
    @Test
    void execute_rebuildsWithoutEvenAskingWhenSchemaIsRecreated() throws Exception {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.existsItemTypeWithItems(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        service().execute();

        verify(dbRepository, never()).hasShopEntries();
        verify(dbRepository).runDdl(contains("CREATE TABLE"));
    }

    private EpicenterSeedingService service() {
        return new EpicenterSeedingService(config(), dbRepository);
    }

    // shopEntryTarget 3000 over 57 shops gives a 53-item catalog, enough for the 40 expanded types.
    private static AppConfig config() {
        return config(true);
    }

    private static AppConfig config(boolean recreateSchema) {
        return new AppConfig("jdbc:unused-by-unit-test", "unused", "unused",
                100, 2, 2, 500, 3000, 2, 500, 0, recreateSchema, "Сантехніка 1");
    }
}
