package ua.shpp.pipeline;

import ua.shpp.config.AppConfig;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.utils.CatalogDimensions;
import ua.shpp.validation.DtoValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ProducerConsumerPipeline {


    public static void execute(CatalogDimensions dimensions, DtoValidator validator, AppConfig config) {
        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue(config.queueCapacity());
        ShopEntryGenerator generator = new ShopEntryGenerator();

        List<Future> producerFutures = new ArrayList<>();
        List<Future> consumerFutures = new ArrayList<>();

        try (
                ExecutorService producerExecutor = Executors.newFixedThreadPool(config.producerThreadPoolSize());
                ExecutorService consumerExecutor = Executors.newFixedThreadPool(config.consumerThreadPoolSize())
        ) {
            for (int shopId = 1; shopId < dimensions.shopsSize(); shopId++) {
                producerFutures.add(producerExecutor.submit(
                        new ShopEntryProducerSubtask(generator, queue, shopId, config.batchSize(), dimensions))
                );
            }

            for (int i = 0; i < config.consumerThreadPoolSize(); i++) {
                consumerFutures.add(consumerExecutor.submit(new ShopEntryConsumer()));
            }

            //wait for producers to finish
            //wait for consumers to finish

            //logout results
        }

    }
}