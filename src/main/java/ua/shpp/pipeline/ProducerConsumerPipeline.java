package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.exceptions.PipelineTaskException;
import ua.shpp.exceptions.RowCountMismatchException;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.validation.DtoValidator;
import ua.shpp.utils.CatalogDimensions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Fills in the final ShopEntry table (3M+ rows) via a producer/consumer pipeline.
 * Orchestration only: sets up the queue and thread pools, starts ShopEntryProducerSubtask/
 * ShopEntryConsumer workers, coordinates shutdown via poison pills, logs the summary.
 * Producer/consumer logic itself lives in their own classes.
 */
public class ProducerConsumerPipeline {
    private static final Logger log = LoggerFactory.getLogger(ProducerConsumerPipeline.class);
    private static final List<ShopEntryDto> POISON_PILL = new ArrayList<>();

    public void execute(DbRepository dbRepository, DtoValidator validator, AppConfig config,
                        CatalogDimensions dimensions) throws InterruptedException {
        int shopCount = dimensions.shopCount();
        long plannedRows = (long) shopCount * dimensions.itemCatalogSize();
        log.info("Starting ShopEntry pipeline: shopCount={}, itemCatalogSize={}, plannedRows={}, "
                        + "target={}, producers={}, consumers={}, batchSize={}, queueCapacity={}",
                shopCount, dimensions.itemCatalogSize(), plannedRows, config.shopEntryTarget(),
                config.producerThreadPoolSize(), config.consumerThreadPoolSize(),
                config.batchSize(), config.queueCapacity());

        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(config.queueCapacity());
        ShopEntryGenerator generator = new ShopEntryGenerator(config.maxStockQuantity(), config.invalidRatePercent());
        List<Future<Integer>> producerFutures = new ArrayList<>();
        List<Future<Integer>> consumerFutures = new ArrayList<>();

        try (
                ExecutorService producers = Executors.newFixedThreadPool(config.producerThreadPoolSize());
                ExecutorService consumers = Executors.newFixedThreadPool(config.consumerThreadPoolSize())
        ) {
            // Start Producers
            long producersStartMillis = System.currentTimeMillis();
            for (int shopId = 1; shopId <= shopCount; shopId++) {
                producerFutures.add(producers.submit(
                        new ShopEntryProducerSubtask(generator, queue, shopId, dimensions, config.batchSize())
                ));
            }
            log.info("Submitted {} producer tasks (pool size {}), waiting for completion...",
                    shopCount, config.producerThreadPoolSize());

            // Start Consumers
            long consumersStartMillis = System.currentTimeMillis();
            for (int i = 0; i < config.consumerThreadPoolSize(); i++) {
                consumerFutures.add(consumers.submit(
                        new ShopEntryConsumer(dbRepository, queue, POISON_PILL, validator)
                ));
            }
            log.info("Submitted {} consumer tasks (pool size {}), waiting for completion...",
                    config.consumerThreadPoolSize(), config.consumerThreadPoolSize());

            // Wait Producers -> Send PoisonPills -> Wait Consumers.
            long generatedRows;
            long producerMillis;
            try {
                generatedRows = awaitAndSum(producerFutures, "producer");
                producerMillis = System.currentTimeMillis() - producersStartMillis;
            } finally {
                sendPoisonPills(queue, config.consumerThreadPoolSize());
            }

            long insertedRows = awaitAndSum(consumerFutures, "consumer");
            long consumerMillis = System.currentTimeMillis() - consumersStartMillis;

            logSummary(config, generatedRows, insertedRows, producerMillis, consumerMillis);
            verifyTargetReached(config, insertedRows);
        }
    }

    private void sendPoisonPills(BlockingQueue<List<ShopEntryDto>> queue, int count) throws InterruptedException {
        log.info("Sending {} poison pills to consumers...", count);
        for (int i = 0; i < count; i++) {
            queue.put(POISON_PILL);
        }
    }

    /**
     * Returns the total of: rows generated for producers; rows inserted for consumers.
     * Blocks until every worker of this role has finished.
     */
    private long awaitAndSum(List<Future<Integer>> futures, String role) throws InterruptedException {
        long total = 0;
        for (Future<Integer> future : futures) {
            try {
                total += future.get();
            } catch (ExecutionException e) {
                throw new PipelineTaskException("A " + role + " task failed", e.getCause());
            }
        }
        return total;
    }

    private void logSummary(AppConfig config, long generatedRows, long insertedRows,
                            long producerMillis, long consumerMillis) {
        log.info("Pipeline finished: generated={}, inserted={}, target={}",
                generatedRows, insertedRows, config.shopEntryTarget());

        log.info("Generation: {} rows in {} ms ({} rows/sec). Insertion: {} rows in {} ms ({} rows/sec)",
                generatedRows, producerMillis, rowsPerSec(generatedRows, producerMillis),
                insertedRows, consumerMillis, rowsPerSec(insertedRows, consumerMillis));
    }

    /**
     * Required at least shopEntryTarget rows in the end - a shortfall means the data
     * set is unusable for the search.
     */
    private void verifyTargetReached(AppConfig config, long insertedRows) {
        if (insertedRows < config.shopEntryTarget()) {
            throw new RowCountMismatchException(String.format(
                    "Inserted %d ShopEntry rows, which is below the required target of %d. "
                            + "Batches were lost during insertion - see the earlier consumer errors.",
                    insertedRows, config.shopEntryTarget()));
        }
    }

    /** todo
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
