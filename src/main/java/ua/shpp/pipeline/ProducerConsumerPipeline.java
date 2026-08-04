package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.exceptions.PipelineTaskException;
import ua.shpp.exceptions.RowCountMismatchException;
import ua.shpp.generation.ShopEntryGenerator;
import ua.shpp.hibernateValidator.DtoValidator;

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
 * Orchestration only: sets up the queue and thread pools, starts ShopEntryProducerSubtask/
 * ShopEntryConsumer workers, coordinates shutdown via poison pills, logs the summary.
 * Producer/consumer logic itself lives in their own classes.
 * <p>
 * Holds no mutable state: every count comes back through the workers' Futures, so the same
 * instance can be executed twice without carrying numbers over from the previous run.
 */
public class ProducerConsumerPipeline {
    private static final Logger log = LoggerFactory.getLogger(ProducerConsumerPipeline.class);
    // Unique sentinel instance (not List.of()) so reference equality (==) is reliable.
    private static final List<ShopEntryDto> POISON_PILL = new ArrayList<>();

    public void execute(DbRepository dbRepository, DtoValidator validator, AppConfig config,
                        int shopCount, int itemCatalogSize) throws InterruptedException {
        long plannedRows = (long) shopCount * itemCatalogSize;
        log.info("Starting ShopEntry pipeline: shopCount={}, itemCatalogSize={}, plannedRows={}, "
                        + "target={}, producers={}, consumers={}, batchSize={}, queueCapacity={}",
                shopCount, itemCatalogSize, plannedRows, config.shopEntryTarget(),
                config.producerThreadPoolSize(), config.consumerThreadPoolSize(),
                config.batchSize(), config.queueCapacity());

        BlockingQueue<List<ShopEntryDto>> queue = new ArrayBlockingQueue<>(config.queueCapacity());
        ShopEntryGenerator generator = new ShopEntryGenerator(config.maxStockQuantity());
        List<Future<Integer>> producerFutures = new ArrayList<>();
        List<Future<Integer>> consumerFutures = new ArrayList<>();

        try (
                ExecutorService producers = Executors.newFixedThreadPool(config.producerThreadPoolSize());
                ExecutorService consumers = Executors.newFixedThreadPool(config.consumerThreadPoolSize())
        ) {
            // Start Producer
            long producersStartMillis = System.currentTimeMillis();
            for (int shopId = 1; shopId <= shopCount; shopId++) {
                producerFutures.add(producers.submit(new ShopEntryProducerSubtask(generator, queue, shopId, shopCount,
                        itemCatalogSize, config.batchSize())));
            }
            log.info("Submitted {} producer tasks (pool size {}), waiting for completion...",
                    shopCount, config.producerThreadPoolSize());

            // Start Consumer
            long consumersStartMillis = System.currentTimeMillis();
            for (int i = 0; i < config.consumerThreadPoolSize(); i++) {
                consumerFutures.add(consumers.submit(
                        new ShopEntryConsumer(dbRepository, queue, POISON_PILL, validator)));
            }
            log.info("Submitted {} consumer tasks (pool size {}), waiting for completion...",
                    config.consumerThreadPoolSize(), config.consumerThreadPoolSize());

            // Wait Producer -> Send PoisonPills -> Wait Consumer.
            long generatedRows;
            long producerMillis;
            try {
                generatedRows = sumCompleted(producerFutures, "producer");
                producerMillis = System.currentTimeMillis() - producersStartMillis;
            } finally {
                sendPoisonPills(queue, config.consumerThreadPoolSize());
            }

            long insertedRows = sumCompleted(consumerFutures, "consumer");
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
     * Blocks until every worker of this role has finished and returns the total they report -
     * rows generated for producers, rows inserted for consumers. Both are measured rather
     * than assumed, so the summary never claims work that did not happen.
     * <p>
     * A failure is rethrown instead of vanishing inside an unwatched Future. Failing fast is
     * deliberate: a dead worker (e.g. the shopId-range guard in ShopEntryGenerator) means the
     * data set is already incomplete, so carrying on would only hide that. Batch-level insert
     * failures are the separate, tolerated case - ShopEntryConsumer swallows those on purpose
     * and they never reach here.
     * <p>
     * InterruptedException is deliberately not caught: it says this waiting thread was asked
     * to stop, not that a worker broke, so wrapping it as a task failure would be a lie.
     */
    private long sumCompleted(List<Future<Integer>> futures, String role) throws InterruptedException {
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

        /*
         * Producer and consumer phases overlap in wall time (consumers start early and drain
         * the queue while producers are still generating), so these are each phase's own
         * start-to-finish duration, not two halves of one total - that's why they don't sum
         * to the "generation+insertion took X ms" figure logged by the caller.
         */
        log.info("Generation: {} rows in {} ms ({} rows/sec). Insertion: {} rows in {} ms ({} rows/sec)",
                generatedRows, producerMillis, rowsPerSec(generatedRows, producerMillis),
                insertedRows, consumerMillis, rowsPerSec(insertedRows, consumerMillis));
    }

    /**
     * Runs after the summary is logged, so the throughput numbers are on record even when
     * this aborts the run. Individual batch failures are tolerated as they happen, but the
     * task requires at least shopEntryTarget rows in the end - a shortfall means the data
     * set is unusable for the search, and that is only knowable from the final count.
     */
    private void verifyTargetReached(AppConfig config, long insertedRows) {
        if (insertedRows < config.shopEntryTarget()) {
            throw new RowCountMismatchException(String.format(
                    "Inserted %d ShopEntry rows, which is below the required target of %d. "
                            + "Batches were lost during insertion - see the earlier consumer errors.",
                    insertedRows, config.shopEntryTarget()));
        }
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
