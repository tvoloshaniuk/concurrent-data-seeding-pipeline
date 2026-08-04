package ua.shpp.pipeline;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.hibernateValidator.DtoValidator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopEntryConsumerTest {
    // Same identity trick as the pipeline: a dedicated instance so == is a reliable signal.
    private static final List<ShopEntryDto> POISON_PILL = new ArrayList<>();

    private final DbRepository dbRepository = mock(DbRepository.class);
    private final BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(10);
    // Real, not mocked: the invalid-entry test depends on the actual Bean Validation rules.
    private final DtoValidator validator = new DtoValidator();

    /*
     * Mock the situation that the first batch inserted all entries, but the second batch lost a row to
     * ON CONFLICT DO NOTHING.
     * The consumer must report the sum of rows the repository reports inserted, not the sum of rows it was given.
     */
    @Test
    void call_returnsTheSumOfRowsTheRepositoryReportsInserted() throws Exception {
        when(dbRepository.batchInsertShopEntries(anyList())).thenReturn(
                2, //inserted full batch
                1 //inserted only 1 of 2 rows, because one was lost to ON CONFLICT DO NOTHING
        );
        queue.put(batchWithItemIds(1, 2));
        queue.put(batchWithItemIds(3, 4));
        queue.put(POISON_PILL);

        int inserted = consumer().call();

        assertEquals(3, inserted); //sum is 3, not 4
    }

    @Test
    void call_stopsAtPoisonPillAndLeavesLaterBatchesUntouched() throws Exception {
        when(dbRepository.batchInsertShopEntries(anyList())).thenReturn(2);
        queue.put(batchWithItemIds(1, 2));
        queue.put(POISON_PILL);
        queue.put(batchWithItemIds(3, 4));

        consumer().call();

        verify(dbRepository, times(1)).batchInsertShopEntries(anyList());
        assertEquals(1, queue.size());
    }

    /* A failed insert must not end the consumer: a dead consumer never takes its poison pill,
    which would leave the pipeline waiting on it forever. */
    @Test
    void call_keepsConsumingAfterAFailedBatchAndExcludesItsRows() throws Exception {
        when(dbRepository.batchInsertShopEntries(anyList()))
                .thenThrow(new RuntimeException("connection reset"))
                .thenReturn(2);
        queue.put(batchWithItemIds(1, 2));
        queue.put(batchWithItemIds(3, 4));
        queue.put(POISON_PILL);

        int inserted = consumer().call();

        assertEquals(2, inserted);
        verify(dbRepository, times(2)).batchInsertShopEntries(anyList());
    }

    @Test
    void call_dropsInvalidEntriesBeforeHandingTheBatchToTheRepository() throws Exception {
        when(dbRepository.batchInsertShopEntries(anyList())).thenReturn(1);
        ShopEntryDto valid = new ShopEntryDto(1, 1, 5);
        ShopEntryDto invalid = new ShopEntryDto(0, 1, 5);
        queue.put(List.of(valid, invalid));
        queue.put(POISON_PILL);

        consumer().call();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ShopEntryDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(dbRepository).batchInsertShopEntries(captor.capture());
        assertEquals(List.of(valid), captor.getValue());
    }

    @Test
    void call_returnsZeroWhenItReceivesOnlyThePoisonPill() throws Exception {
        queue.put(POISON_PILL);

        assertEquals(0, consumer().call());
    }

    /* An equal-valued list must not be mistaken for the sentinel, otherwise a real batch
    could stop the consumer early - this is why POISON_PILL is compared with == not equals(). */
    @Test
    void call_treatsAnEmptyDataBatchAsDataRatherThanTheSentinel() throws Exception {
        queue.put(new ArrayList<>());
        queue.put(POISON_PILL);

        consumer().call();

        assertTrue(queue.isEmpty());
        verify(dbRepository).batchInsertShopEntries(anyList());
    }

    private ShopEntryConsumer consumer() {
        return new ShopEntryConsumer(dbRepository, queue, POISON_PILL, validator);
    }

    // Varargs so a batch size is never implied by the argument count of a fixed signature.
    private static List<ShopEntryDto> batchWithItemIds(int... itemIds) {
        return Arrays.stream(itemIds)
                .mapToObj(itemId -> new ShopEntryDto(itemId, 1, 5))
                .toList();
    }
}
