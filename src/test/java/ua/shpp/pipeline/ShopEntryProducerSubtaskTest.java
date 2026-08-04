package ua.shpp.pipeline;

import org.junit.jupiter.api.Test;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopEntryProducerSubtaskTest {
    private static final int SHOP_COUNT = 3;
    private static final int MAX_STOCK_QUANTITY = 500;

    private final BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(100);

    @Test
    void call_returnsHowManyRowsItGenerated() throws Exception {
        int generated = subtask(1, 10, 4).call();

        assertEquals(10, generated);
    }

    @Test
    void call_queuesEveryGeneratedRowExactlyOnce() throws Exception {
        subtask(1, 10, 4).call();

        assertEquals(10, drain().size());
    }

    /* 10 rows at batchSize 4 must come out as 4+4+2, not 4+4+4 - the Math.min guard on the
    last slice is the only thing stopping subList from running past the end. */
    @Test
    void call_makesTheLastBatchShorterWhenSizeIsNotDivisible() throws Exception {
        subtask(1, 10, 4).call();

        List<Integer> batchSizes = new ArrayList<>();
        queue.forEach(batch -> batchSizes.add(batch.size()));
        assertEquals(List.of(4, 4, 2), batchSizes);
    }

    @Test
    void call_producesOneWholeBatchWhenCatalogIsSmallerThanBatchSize() throws Exception {
        subtask(1, 3, 500).call();

        assertEquals(1, queue.size());
        assertEquals(3, queue.peek().size());
    }

    @Test
    void call_stampsItsOwnShopIdOnEveryQueuedRow() throws Exception {
        subtask(2, 10, 4).call();

        assertTrue(drain().stream().allMatch(entry -> entry.shopId() == 2));
    }

    // The out-of-range guard lives in ShopEntryGenerator, so it surfaces through call().
    @Test
    void call_propagatesGeneratorRejectionOfOutOfRangeShopId() {
        ShopEntryProducerSubtask subtask = subtask(SHOP_COUNT + 1, 10, 4);

        assertThrows(IllegalArgumentException.class, subtask::call);
    }

    private ShopEntryProducerSubtask subtask(int shopId, int itemCatalogSize, int batchSize) {
        return new ShopEntryProducerSubtask(new ShopEntryGenerator(MAX_STOCK_QUANTITY), queue,
                shopId, SHOP_COUNT, itemCatalogSize, batchSize);
    }

    private List<ShopEntryDto> drain() {
        List<ShopEntryDto> all = new ArrayList<>();
        queue.forEach(all::addAll);
        return all;
    }
}
