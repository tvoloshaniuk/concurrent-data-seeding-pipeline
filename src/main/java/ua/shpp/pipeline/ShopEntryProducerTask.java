package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * CPU-bound: generates one shop's entries and splits them into batches on the queue.
 * Never touches the database - that's the consumer's job. One instance handles one shop;
 * the pipeline creates one task per shopId.
 */
public class ShopEntryProducerTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ShopEntryProducerTask.class);

    private final ShopEntryGenerator generator;
    private final BlockingQueue<List<ShopEntryDto>> queue;
    private final int shopId;
    private final int shopCount;
    private final int itemCatalogSize;
    private final int batchSize;

    public ShopEntryProducerTask(ShopEntryGenerator generator, BlockingQueue<List<ShopEntryDto>> queue,
                                  int shopId, int shopCount, int itemCatalogSize, int batchSize) {
        this.generator = generator;
        this.queue = queue;
        this.shopId = shopId;
        this.shopCount = shopCount;
        this.itemCatalogSize = itemCatalogSize;
        this.batchSize = batchSize;
    }

    @Override
    public void run() {
        List<ShopEntryDto> shopEntries = generator.generateForShop(shopId, shopCount, itemCatalogSize);
        log.debug("Shop {}/{}: generated {} entries, queuing in batches of {}",
                shopId, shopCount, shopEntries.size(), batchSize);

        for (int batchStart = 0; batchStart < shopEntries.size(); batchStart += batchSize) {
            if (Thread.currentThread().isInterrupted()) {
                log.warn("Shop {}: producer interrupted, aborting with {} of {} entries still unqueued",
                        shopId, shopEntries.size() - batchStart, shopEntries.size());
                return;
            }
            int batchEnd = Math.min(batchStart + batchSize, shopEntries.size());
            put(shopEntries.subList(batchStart, batchEnd));
        }
        log.debug("Shop {}/{}: all batches queued", shopId, shopCount);
    }

    private void put(List<ShopEntryDto> batch) {
        try {
            queue.put(batch);
        } catch (InterruptedException e) {
            log.warn("Interrupted while queuing a batch", e);
            Thread.currentThread().interrupt();
        }
    }
}
