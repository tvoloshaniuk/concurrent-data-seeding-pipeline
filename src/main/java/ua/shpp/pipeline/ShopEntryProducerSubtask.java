package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

/**
 * CPU-bound: generates one shop's entries and splits them into batches on the queue.
 * Never touches the database - that's the consumer's job.
 * <p>
 * Scoped to a single shop, unlike the shop-agnostic ShopEntryConsumer: the pipeline builds
 * one instance per shopId, and each one only ever knows its own shop. That is what lets
 * several producers run without coordinating - their (itemId, shopId) ranges cannot overlap
 * by construction. It also caps the useful producer count at shopCount.
 * <p>
 * Returns how many rows it generated, so the pipeline can report a measured total instead
 * of the planned one. Being a Callable also lets call() declare throws InterruptedException,
 * so an interrupt aborts the task at the first queue.put() with no manual flag check.
 */
public class ShopEntryProducerSubtask implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(ShopEntryProducerSubtask.class);

    private final ShopEntryGenerator generator;
    private final BlockingQueue<List<ShopEntryDto>> queue;
    private final int shopId;
    private final int shopCount;
    private final int itemCatalogSize;
    private final int batchSize;

    public ShopEntryProducerSubtask(ShopEntryGenerator generator, BlockingQueue<List<ShopEntryDto>> queue,
                                    int shopId, int shopCount, int itemCatalogSize, int batchSize) {
        this.generator = generator;
        this.queue = queue;
        this.shopId = shopId;
        this.shopCount = shopCount;
        this.itemCatalogSize = itemCatalogSize;
        this.batchSize = batchSize;
    }

    @Override
    public Integer call() throws InterruptedException {
        List<ShopEntryDto> shopEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        log.debug("Shop {}/{}: generated {} entries, queuing in batches of {}",
                shopId, shopCount, shopEntries.size(), batchSize);

        for (int batchStart = 0; batchStart < shopEntries.size(); batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, shopEntries.size());
            queue.put(shopEntries.subList(batchStart, batchEnd));
        }
        log.debug("Shop {}/{}: all batches queued", shopId, shopCount);
        return shopEntries.size();
    }
}
