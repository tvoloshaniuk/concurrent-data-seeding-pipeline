package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.utils.CatalogDimensions;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public class ShopEntryProducerSubtask implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(ShopEntryProducerSubtask.class);
    private final int batchSize;
    private final CatalogDimensions dimensions;
    private final ShopEntryGenerator generator;
    private final BlockingQueue<List<ShopEntryDto>> queue;
    private final int shopId;


    public ShopEntryProducerSubtask
            (
                    ShopEntryGenerator generator,
                    BlockingQueue<List<ShopEntryDto>> queue,
                    int shopId,
                    int batchSize, CatalogDimensions dimensions)
    {
        this.generator = generator;
        this.queue = queue;
        this.shopId = shopId;
        this.batchSize = batchSize;
        this.dimensions = dimensions;
    }

    @Override
    public Integer call() throws Exception {
        List<ShopEntryDto> entries = generator.generateForShop(
                shopId, dimensions.shopsSize(), dimensions.itemCatalogSize()
        );
        log.debug("Shop {}/{}: generated {} entries, queuing in batches of {}",
                shopId, dimensions.shopsSize(), entries.size(), batchSize);
        for (int i = 0; i < entries.size(); i += batchSize) {
            int batchEndIndex = Math.min(i + batchSize, entries.size());
            List<ShopEntryDto> batch = entries.subList(i, batchEndIndex);
            queue.put(batch);
        }
        log.debug("Shop {}/{}: all batches queued", shopId, dimensions.shopsSize());
        return entries.size();
    }
}














