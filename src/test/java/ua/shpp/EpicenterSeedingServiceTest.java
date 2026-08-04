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

    /* The whole point of the guard: a mistyped argument must cost seconds, not a full run.
    Never touching ShopEntry is what proves it aborted before the expensive part. */
    @Test
    void execute_failsBeforeGeneratingShopEntriesWhenItemTypeIsUnknown() {
        when(dbRepository.existsItemType(anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service().execute());

        verify(dbRepository, never()).batchInsertShopEntries(anyList());
    }

    /* The exact mistake this catches is passing a bare category instead of the suffixed name,
    so the message has to say that - otherwise the guard just replaces one puzzle with another. */
    @Test
    void execute_explainsTheSuffixConventionWhenItemTypeIsUnknown() {
        when(dbRepository.existsItemType(anyString())).thenReturn(false);

        IllegalArgumentException thrown =
                assertThrows(IllegalArgumentException.class, () -> service().execute());

        assertTrue(thrown.getMessage().contains("Сантехніка"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("suffix"), thrown.getMessage());
    }

    @Test
    void execute_completesWhenItemTypeExistsAndEveryRowLands() {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(dbRepository.findShopWithMaxItems(anyString())).thenReturn("Київ, вул. Берковецька 6К");

        assertDoesNotThrow(() -> service().execute());
    }

    /* A missing top shop is reported, not thrown: the run still produced valid timings, and
    losing them would hurt more than failing helps at that point. */
    @Test
    void execute_toleratesNoMatchingShopWithoutFailingTheRun() {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        when(dbRepository.findShopWithMaxItems(anyString())).thenReturn(null);

        assertDoesNotThrow(() -> service().execute());
    }

    /* Index creation must come after the bulk load, otherwise every inserted row pays for an
    incremental B-tree update - the ordering is a performance decision, not a formality. */
    @Test
    void execute_buildsSecondaryIndexesOnlyAfterShopEntryIsFilled() throws Exception {
        when(dbRepository.existsItemType(anyString())).thenReturn(true);
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());

        service().execute();

        InOrder inOrder = inOrder(dbRepository);
        inOrder.verify(dbRepository, atLeastOnce()).batchInsertShopEntries(anyList());
        inOrder.verify(dbRepository).runDdl(contains("CREATE INDEX"));
    }

    private EpicenterSeedingService service() {
        return new EpicenterSeedingService(config(), dbRepository);
    }

    /* shopEntryTarget 3000 over the real 57-shop shops.csv gives a 53-item catalog, which must
    still cover the 40 expanded types (20 categories x coefficient 2) that DataPopulator builds. */
    private static AppConfig config() {
        return new AppConfig("jdbc:unused-by-unit-test", "unused", "unused",
                100, 2, 2, 500, "Сантехніка 1", 3000, 2, 500);
    }
}
