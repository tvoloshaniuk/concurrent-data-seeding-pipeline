package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.exceptions.PipelineTaskException;
import ua.shpp.generation.ShopEntryGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        List<Future<?>> producerFutures = new ArrayList<>();
        List<Future<?>> consumerFutures = new ArrayList<>();

        try (
                ExecutorService producers = Executors.newFixedThreadPool(config.producerThreadPoolSize());
                ExecutorService consumers = Executors.newFixedThreadPool(config.consumerThreadPoolSize())
        ) {
            long producersStartMillis = System.currentTimeMillis();
            for (int shopId = 1; shopId <= shopCount; shopId++) {
                producerFutures.add(producers.submit(new ShopEntryProducerTask(generator, queue, shopId, shopCount,
                        itemCatalogSize, config.batchSize())));
            }
            log.info("Submitted {} producer tasks (pool size {}), waiting for completion...",
                    shopCount, config.producerThreadPoolSize());

            long consumersStartMillis = System.currentTimeMillis();
            for (int i = 0; i < config.consumerThreadPoolSize(); i++) {
                consumerFutures.add(consumers.submit(
                        new ShopEntryConsumer(dbRepository, queue, POISON_PILL, insertedCount))
                );
            }

            // WaitProducer -> Send PoisonPills -> WaitConsumer.
            long producerMillis;
            try {
                awaitCompletion(producerFutures, "producer");
                producerMillis = System.currentTimeMillis() - producersStartMillis;
            } finally {
                sendPoisonPills(queue, config.consumerThreadPoolSize());
            }

            awaitCompletion(consumerFutures, "consumer");
            long consumerMillis = System.currentTimeMillis() - consumersStartMillis;

            logSummary(config, plannedRows, producerMillis, consumerMillis);
        }
    }

    private void sendPoisonPills(BlockingQueue<List<ShopEntryDto>> queue, int count) throws InterruptedException {
        log.info("Sending {} poison pills to consumers...", count);
        for (int i = 0; i < count; i++) {
            queue.put(POISON_PILL);
        }
    }

    /**
     * Blocks until every task has finished, then rethrows the first failure instead of
     * letting it vanish inside an unchecked Future. Failing fast is deliberate: a dead
     * producer (e.g. the shopId-range guard in ShopEntryGenerator) means the data set is
     * already incomplete, so carrying on would only hide that. Batch-level insert failures
     * are the separate, tolerated case - ShopEntryConsumer swallows those on purpose.
     */
    private void awaitCompletion(List<Future<?>> futures, String role) throws InterruptedException {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new PipelineTaskException("A " + role + " task failed", e.getCause());
            }
        }
    }

    private void logSummary(AppConfig config, long plannedRows, long producerMillis, long consumerMillis) {
        /*
         * Not failFast: rows dropped by a failed batch insert are not retried - the sequential
         * shopId/itemId walk has already moved on. Just report it; the search still runs on
         * whatever got inserted.
         */
        int actual = insertedCount.get();
        if (actual < config.shopEntryTarget()) {
            log.warn("Pipeline finished but inserted fewer rows than target: inserted={}, target={}. "
                            + "Proceeding with the search on incomplete data.",
                    actual, config.shopEntryTarget());
        } else {
            log.info("Pipeline finished successfully: inserted={}, target={}", actual, config.shopEntryTarget());
        }

        /*
         * Producer and consumer phases overlap in wall time (consumers start early and drain
         * the queue while producers are still generating), so these are each phase's own
         * start-to-finish duration, not two halves of one total - that's why they don't sum
         * to the "generation+insertion took X ms" figure logged by the caller.
         */
        log.info("Generation: {} rows in {} ms ({} rows/sec). Insertion: {} rows in {} ms ({} rows/sec)",
                plannedRows, producerMillis, rowsPerSec(plannedRows, producerMillis),
                actual, consumerMillis, rowsPerSec(actual, consumerMillis));
    }

    /**
     * %.1f gives one decimal place; Locale.ROOT pins the decimal separator to "." regardless
     * of the JVM's default locale, so log output reads the same on every machine (some
     * locales format decimals with ","). millis <= 0 is a genuine "too fast to measure" case
     * rather than an error, so it is reported as n/a instead of faking a 1 ms floor.
     */
    private static String rowsPerSec(long rows, long millis) {
        if (millis <= 0) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.1f", rows * 1000.0 / millis);
    }
}
