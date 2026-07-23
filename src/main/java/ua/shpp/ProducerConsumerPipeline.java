package ua.shpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.pipeline.ShopEntryConsumer;
import ua.shpp.pipeline.ShopEntryProducerTask;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Orchestration only: sets up the queue and thread pools, starts ShopEntryProducerTask/
 * ShopEntryConsumer workers, coordinates shutdown via poison pills, logs the summary.
 * Producer/consumer logic itself lives in their own classes.
 */
public class ProducerConsumerPipeline {

    private static final Logger log = LoggerFactory.getLogger(ProducerConsumerPipeline.class);


    // Unique sentinel instance (not List.of()) so reference equality (==) is reliable.
    private static final List<ShopEntryDto> POISON_PILL = new ArrayList<>();

    private final AtomicInteger insertedCount = new AtomicInteger(0);

    public void run(DbRepository dbRepository, AppConfig config, int shopCount, int itemCatalogSize)
            throws InterruptedException {
        long plannedRows = (long) shopCount * itemCatalogSize;
        log.info("Starting ShopEntry pipeline: shopCount={}, itemCatalogSize={}, plannedRows={}, "
                        + "target={}, producers={}, consumers={}, batchSize={}, queueCapacity={}",
                shopCount, itemCatalogSize, plannedRows, config.shopEntryTarget(),
                config.producerThreadPoolSize(), config.consumerThreadPoolSize(),
                config.batchSize(), config.queueCapacity());

        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(config.queueCapacity());
        ShopEntryGenerator generator = new ShopEntryGenerator(config.maxStockQuantity());

        /**
         * Consumers must be running BEFORE producers start filling the bounded queue -
         * otherwise producers block on queue.put() once it fills up, with nobody around to
         * drain it, and the pipeline deadlocks. Consumers are cheap to start early: with an
         * empty queue they just block on take() until the first batch arrives.
         */
        List<Future<?>> consumerFutures = new ArrayList<>();
        ExecutorService consumers = Executors.newFixedThreadPool(config.consumerThreadPoolSize());
        for (int i = 0; i < config.consumerThreadPoolSize(); i++) {
            consumerFutures.add(consumers.submit(new ShopEntryConsumer(dbRepository, queue, POISON_PILL, insertedCount)));
        }

        /**
         * Producer (CPU-bound generation) and consumer (I/O-bound insert) have different
         * optimal pool sizes - separate configs instead of one shared threadPoolSize.
         */
        List<Future<?>> producerFutures = new ArrayList<>();
        try (ExecutorService producers = Executors.newFixedThreadPool(config.producerThreadPoolSize())) {
            for (int shopId = 1; shopId <= shopCount; shopId++) {
                producerFutures.add(producers.submit(new ShopEntryProducerTask(generator, queue, shopId, shopCount,
                        itemCatalogSize, config.batchSize())));
            }
            log.info("Submitted {} producer tasks (pool size {}), waiting for completion...",
                    shopCount, config.producerThreadPoolSize());
        } // close() blocks here until every producer task finishes (or forces shutdownNow() if interrupted)
        awaitAndLogFailures(producerFutures, "producer");

        /**
         * Poison pills are sent only after every producer has finished - that's how
         * consumers learn there is nothing more to come.
         */
        log.info("All producers finished. Sending {} poison pills to consumers...", config.consumerThreadPoolSize());
        for (int i = 0; i < config.consumerThreadPoolSize(); i++) {
            queue.put(POISON_PILL);
        }

        consumers.close();
        awaitAndLogFailures(consumerFutures, "consumer");

        /**
         * Not failFast: lost batches are not retried - the sequential shopId/itemId walk has
         * already moved on. Just report it; the search still runs on whatever got inserted.
         */
        int actual = insertedCount.get();
        if (actual < config.shopEntryTarget()) {
            log.warn("Pipeline finished but inserted fewer rows than target: inserted={}, target={}. "
                            + "Proceeding with the search on incomplete data.",
                    actual, config.shopEntryTarget());
        } else {
            log.info("Pipeline finished successfully: inserted={}, target={}", actual, config.shopEntryTarget());
        }
    }

    /**
     * Surfaces any exception a task threw (e.g. the shopId-range guard in ShopEntryGenerator)
     * instead of letting it disappear silently in an unchecked Future.
     */
    private void awaitAndLogFailures(List<Future<?>> futures, String role) {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                log.error("A {} task failed", role, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for {} tasks to report", role);
            }
        }
    }
}
