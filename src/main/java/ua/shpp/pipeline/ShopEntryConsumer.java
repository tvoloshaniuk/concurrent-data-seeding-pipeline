package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.hibernateValidator.DtoValidator;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

/**
 * I/O-bound: validates and inserts batches until it receives the poison pill sentinel.
 * <p>
 * Shop-agnostic, unlike the single-shop ShopEntryProducerSubtask: one instance per consumer
 * thread rather than per shop, and each takes whatever batch reaches the head of the queue,
 * from any shop. That asymmetry is why the consumer pool size is a free tuning knob - more
 * consumers need no change to the data layout - while producers are capped by shopCount.
 * <p>
 * Returns its own inserted-row count, so the pipeline sums the Futures instead of every
 * consumer hammering one shared counter. Being a Callable also lets call() declare throws
 * InterruptedException, so an interrupt ends this consumer at queue.take() by itself.
 */
public class ShopEntryConsumer implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(ShopEntryConsumer.class);

    private final DbRepository dbRepository;
    private final BlockingQueue<List<ShopEntryDto>> queue;
    private final List<ShopEntryDto> poisonPill;
    private final DtoValidator validator;

    public ShopEntryConsumer(DbRepository dbRepository, BlockingQueue<List<ShopEntryDto>> queue,
                              List<ShopEntryDto> poisonPill, DtoValidator validator) {
        this.dbRepository = dbRepository;
        this.queue = queue;
        this.poisonPill = poisonPill;
        this.validator = validator;
    }

    @Override
    public Integer call() throws InterruptedException {
        String consumerName = Thread.currentThread().getName();
        int insertedRows = 0;

        // Reported only, never returned - they exist for the progress and shutdown log lines.
        int consumedBatches = 0;
        int failedBatches = 0;

        List<ShopEntryDto> batch;
        while ((batch = queue.take()) != poisonPill) {
            List<ShopEntryDto> validBatch = batch.stream()
                    .filter(validator::isValid)
                    .toList();
            if (validBatch.size() != batch.size()) {
                log.warn("Consumer {}: {} of {} entries in batch failed validation and were dropped",
                        consumerName, batch.size() - validBatch.size(), batch.size());
            }
            /*
             * A single failed batch must not kill this consumer thread, or it never picks
             * up its poison pill and starves another still-alive consumer of the real work.
             */
            try {
                insertedRows += dbRepository.batchInsertShopEntries(validBatch);
                consumedBatches++;
                if (consumedBatches % 100 == 0) {
                    log.info("Consumer {}: {} batches processed, {} rows inserted so far",
                            consumerName, consumedBatches, insertedRows);
                }
            } catch (RuntimeException e) {
                failedBatches++;
                log.error("Consumer {}: batch of {} entries failed to insert and is lost (failedBatches={})",
                        consumerName, validBatch.size(), failedBatches, e);
            }
        }
        log.info("Consumer {}: received poison pill, stopping after {} batches ({} failed), {} rows inserted",
                consumerName, consumedBatches, failedBatches, insertedRows);
        return insertedRows;
    }
}
