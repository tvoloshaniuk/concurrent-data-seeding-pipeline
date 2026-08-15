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
    private final CatalogDimensions dimensions;
    private final int batchSize;

    /**
     * Takes CatalogDimensions rather than two loose ints because shopCount and itemCatalogSize
     * always travel together, and adjacent int parameters are exactly what the compiler cannot
     * catch when they get swapped.
     */
    public ShopEntryProducerSubtask(ShopEntryGenerator generator, BlockingQueue<List<ShopEntryDto>> queue,
                                    int shopId, CatalogDimensions dimensions, int batchSize) {
        this.generator = generator;
        this.queue = queue;
        this.shopId = shopId;
        this.dimensions = dimensions;
        this.batchSize = batchSize;
    }

    /**
     * Invalid rows are not made here but inside ShopEntryGenerator, which is what makes hitting
     * invalidRatePercent straightforward: the generator decides per itemId, so the share falls out
     * of the loop by itself. This method only slices an already-built list into batches and has no
     * business knowing what is inside them - the count it returns therefore includes the invalid
     * ones, which is honest, since they really were generated.
     */
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
