package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.validation.DtoValidator;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;

/**
 * Validates and inserts ShopEntry batches from queue receives the poison pill in each thread of its class instance.
 * Unlike the single-shop ShopEntryProducerSubtask: one instance per consumer
 * thread, not per shop.
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
