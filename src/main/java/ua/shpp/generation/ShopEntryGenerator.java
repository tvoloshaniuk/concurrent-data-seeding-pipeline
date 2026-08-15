package ua.shpp.generation;

import ua.shpp.dto.ShopEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds one shop's worth of ShopEntry rows, occasionally slipping in a deliberately invalid one
 * so the check inside ShopEntryConsumer has something real to reject. That check sits where a
 * real Epicentr would receive rows from a warehouse system; the generator is only standing in for
 * that source, and without invalid input the check could never fire at all.
 */
public class ShopEntryGenerator {

    private final Random random = new Random();
    // config.maxStockQuantity() - inclusive upper bound for the random item_count value
    private final int maxStockQuantity;
    private final int invalidRatePercent;

    public ShopEntryGenerator(int maxStockQuantity, int invalidRatePercent) {
        this.maxStockQuantity = maxStockQuantity;
        this.invalidRatePercent = invalidRatePercent;
    }

    /**
     * Enumerates every itemId (1..itemCatalogSize) for one shop exactly once and always emits a
     * valid row for it, so duplicate (item_id, shop_id) pairs are structurally impossible and the
     * valid row count stays exactly itemCatalogSize whatever invalidRatePercent is set to.
     * <p>
     * Invalid rows are emitted ALONGSIDE the valid one, never instead of it - replacing would
     * shrink the insert count below plannedRows and trip verifyTargetReached, which is precisely
     * the inconsistency this design avoids. The returned list is therefore longer than
     * itemCatalogSize when the rate is above zero, and the extras die in the consumer.
     *
     * @param itemCatalogSize the number of distinct items in the catalog (1..itemCatalogSize)
     */
    public List<ShopEntryDto> generateForShop(int shopId, int shopCount, int itemCatalogSize) {
        /* Not a place for invalid generation: an out-of-range shopId means the pipeline wired the
        task up wrong, which is a programming error worth failing on, not a bad record worth
        filtering. Invalid records are made per entry inside the loop below. */
        if (shopId < 1 || shopId > shopCount) {
            throw new IllegalArgumentException("shopId " + shopId + " out of range [1, " + shopCount + "]");
        }
        List<ShopEntryDto> shopEntries = new ArrayList<>(itemCatalogSize);
        for (int itemId = 1; itemId <= itemCatalogSize; itemId++) {
            if (shouldGenerateInvalid()) {
                shopEntries.add(invalidEntry(itemId, shopId));
            }
            int itemCount = random.nextInt(maxStockQuantity + 1); // [0, maxStockQuantity], 0 included
            shopEntries.add(new ShopEntryDto(itemId, shopId, itemCount));
        }
        return shopEntries;
    }

    private boolean shouldGenerateInvalid() {
        return random.nextInt(100) < invalidRatePercent;
    }

    // Each variant breaks exactly one rule on ShopEntryDto, so neither can pass validation.
    private ShopEntryDto invalidEntry(int itemId, int shopId) {
        return random.nextBoolean()
                ? new ShopEntryDto(itemId, shopId, -1)   // @Min(0) on itemCount
                : new ShopEntryDto(0, shopId, 1);        // @Positive on itemId
    }
}
