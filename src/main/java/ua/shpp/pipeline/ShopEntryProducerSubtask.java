package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.utils.CatalogDimensions;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

/**
 * Generates one shop's entries and splits them into batches on the queue.
 * Doesn't insert them into the database - that's the consumer's job.
 * Scoped to a single shop: the pipeline builds one instance per shopId, and each one only ever knows its own shop.
 *  That is what lets several producers run without coordinating - their (itemId, shopId) ranges cannot overlap
 * Returns how many rows it generated.
 */
public class ShopEntryProducerSubtask implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(ShopEntryProducerSubtask.class);

    private final ShopEntryGenerator generator;
    private final BlockingQueue<List<ShopEntryDto>> queue;
    private final int shopId;
    private final CatalogDimensions dimensions;
    private final int batchSize;

    public ShopEntryProducerSubtask(ShopEntryGenerator generator, BlockingQueue<List<ShopEntryDto>> queue,
                                    int shopId, CatalogDimensions dimensions, int batchSize) {
        this.generator = generator;
        this.queue = queue;
        this.shopId = shopId;
        this.dimensions = dimensions;
        this.batchSize = batchSize;
    }

    @Override
    public Integer call() throws InterruptedException {
        List<ShopEntryDto> shopEntries =
                generator.generateForShop(shopId, dimensions.shopCount(), dimensions.itemCatalogSize());
        log.debug("Shop {}/{}: generated {} entries, queuing in batches of {}",
                shopId, dimensions.shopCount(), shopEntries.size(), batchSize);

        for (int batchStart = 0; batchStart < shopEntries.size(); batchStart += batchSize) {
            int batchEnd = Math.min(batchStart + batchSize, shopEntries.size());
            queue.put(shopEntries.subList(batchStart, batchEnd));
        }
        log.debug("Shop {}/{}: all batches queued", shopId, dimensions.shopCount());
        return shopEntries.size();
    }
}
