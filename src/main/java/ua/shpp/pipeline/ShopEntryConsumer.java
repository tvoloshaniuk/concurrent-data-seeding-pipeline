package ua.shpp.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.db.DbRepository;
import ua.shpp.dto.ShopEntryDto;
import ua.shpp.hibernateValidator.ValidatorUtil;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/* I/O-bound: validates and inserts batches until it receives the poison pill sentinel. */
public class ShopEntryConsumer implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ShopEntryConsumer.class);

    private final DbRepository dbRepository;
    private final BlockingQueue<List<ShopEntryDto>> queue;
    private final List<ShopEntryDto> poisonPill;
    private final AtomicInteger insertedCount;

    public ShopEntryConsumer(DbRepository dbRepository, BlockingQueue<List<ShopEntryDto>> queue,
                              List<ShopEntryDto> poisonPill, AtomicInteger insertedCount) {
        this.dbRepository = dbRepository;
        this.queue = queue;
        this.poisonPill = poisonPill;
        this.insertedCount = insertedCount;
    }

    @Override
    public void run() {
        String consumerName = Thread.currentThread().getName();
        int consumedBatches = 0;
        int failedBatches = 0;
        List<ShopEntryDto> batch;
        while ((batch = take()) != poisonPill) {
            List<ShopEntryDto> validBatch = batch.stream()
                    .filter(ValidatorUtil::isValid)
                    .toList();
            if (validBatch.size() != batch.size()) {
                log.warn("Consumer {}: {} of {} entries in batch failed validation and were dropped",
                        consumerName, batch.size() - validBatch.size(), batch.size());
            }
            /**
             * A single failed batch must not kill this consumer thread, or it never picks
             * up its poison pill and starves another still-alive consumer of the real work.
             */
            try {
                int actuallyInserted = dbRepository.batchInsertShopEntries(validBatch);
                int total = insertedCount.addAndGet(actuallyInserted);
                consumedBatches++;
                if (consumedBatches % 100 == 0) {
                    log.info("Consumer {}: {} batches processed, {} rows inserted so far",
                            consumerName, consumedBatches, total);
                }
            } catch (RuntimeException e) {
                failedBatches++;
                log.error("Consumer {}: batch of {} entries failed to insert and is lost (failedBatches={})",
                        consumerName, validBatch.size(), failedBatches, e);
            }
        }
        log.info("Consumer {}: received poison pill, stopping after {} batches ({} failed)",
                consumerName, consumedBatches, failedBatches);
    }

    private List<ShopEntryDto> take() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for a batch from the queue, stopping this consumer", e);
            Thread.currentThread().interrupt();
            return poisonPill;
        }
    }
}
