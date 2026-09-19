package ua.shpp;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.utils.ResourceLoader;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class EpicenterSeedingServiceTest {
    private final DbRepository dbRepository = mock(DbRepository.class);

    /* happy path */
    @Test
    void execute_dropsTablesAndSeedsThem_whenRecreateSchemaTrue() throws InterruptedException {
        new EpicenterSeedingService(config(true, false), dbRepository).execute();
        InOrder inOrder = inOrder(dbRepository);
        inOrder.verify(dbRepository).runDdl(ResourceLoader.readText("drop_all_tables.sql"));
        //todo more tests about seed(). A lot of inner deatails
        inOrder.verify(dbRepository).runDdl(ResourceLoader.readText("schema.sql"));
    }

    @Test
    void execute_searchesBeforeAndAfterIndexRebuild_whenRecreateIndexesTrue() throws InterruptedException {
        new EpicenterSeedingService(config(false, true), dbRepository).execute();
        InOrder inOrder = inOrder(dbRepository);
        inOrder.verify(dbRepository).runDdl(ResourceLoader.readText("drop_post_load_indexes.sql"));
        //todo inOrder.verify(dbRepository).findShopWithMaxItems("item type 1");
        inOrder.verify(dbRepository).runDdl(ResourceLoader.readText("post_load_indexes.sql"));
        //todo inOrder.verify(dbRepository).findShopWithMaxItems("item type 1");
    }

    @Disabled("todo: DbRepository.findShopWithMaxItems not written yet")
    @Test
    void execute_searchesTopShopOnce_whenRecreateIndexesFalse() throws InterruptedException {
        new EpicenterSeedingService(config(false, false), dbRepository).execute();
        //todo verify(dbRepository, times(1)).findShopWithMaxItems("item type 1");
    }

    /* edge cases, negative, etc */

    @Test
    void execute_runsNoDdl_whenRecreateSchemaFalseAndRecreateIndexesFalse() throws InterruptedException {
        new EpicenterSeedingService(config(false, false), dbRepository).execute();
        verify(dbRepository, never()).runDdl(anyString());
    }

    @Disabled("todo: DbRepository.existsItemType / batchInsertShopEntries not written yet")
    @Test
    void execute_failsBeforeSeeding_whenItemTypeIsAbsentInDatabase() {
        //todo when(dbRepository.existsItemType("item type 1")).thenReturn(false);
        //todo EpicenterSeedingService service = new EpicenterSeedingService(config(true, false), dbRepository);
        //todo assertThrows(IllegalArgumentException.class, service::execute);
        //todo verify(dbRepository, never()).batchInsertShopEntries(anyList());
    }

    @Disabled("todo: DbRepository.existsItemTypeWithItems / batchInsertShopEntries not written yet")
    @Test
    void execute_failsBeforeSeeding_whenItemTypeHasNoItems() {
        //todo when(dbRepository.existsItemType("item type 1")).thenReturn(true);
        //todo when(dbRepository.existsItemTypeWithItems("item type 1")).thenReturn(false);
        //todo EpicenterSeedingService service = new EpicenterSeedingService(config(true, false), dbRepository);
        //todo assertThrows(IllegalArgumentException.class, service::execute);
        //todo verify(dbRepository, never()).batchInsertShopEntries(anyList());
    }

    @Disabled("todo: DbRepository.findShopWithMaxItems not written yet")
    @Test
    void execute_completesWithoutError_whenNoShopFound() {
        //todo when(dbRepository.findShopWithMaxItems("item type 1")).thenReturn(null);
        EpicenterSeedingService service = new EpicenterSeedingService(config(false, false), dbRepository);
        assertDoesNotThrow(service::execute);
    }

    private AppConfig config(boolean recreateSchema, boolean recreateIndexes) {
        return new AppConfig(
                "jdbc:unused",
                "u",
                "p",
                1,
                1,
                1,
                1,
                1,
                1,
                1,
                0,
                recreateSchema,
                recreateIndexes,
                "item type 1"
        );
    }
}