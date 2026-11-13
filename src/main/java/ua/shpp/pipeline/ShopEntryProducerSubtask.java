package ua.shpp.pipeline;

import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.utils.CatalogDimensions;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

public class ShopEntryProducerSubtask implements Callable<Integer> {
    ShopEntryGenerator generator;
    BlockingQueue<List<ShopEntryDto>> queue;
    int shopId;

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
    }

    @Override
    public Integer call() throws Exception {

        return null;
    }
}














