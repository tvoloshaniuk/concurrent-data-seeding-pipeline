package ua.shpp.generation;

import ua.shpp.dto.ShopEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShopEntryGenerator {

    private final Random random = new Random();
    // config.maxStockQuantity() - inclusive upper bound for the random item_count value
    private final int maxStockQuantity;

    public ShopEntryGenerator(int maxStockQuantity) {
        this.maxStockQuantity = maxStockQuantity;
    }

    /**
     * Enumerates every itemId (1..itemCatalogSize) for one shop exactly once and always
     * inserts a row, so duplicate (item_id, shop_id) pairs are structurally impossible and
     * the row count (shopCount * itemCatalogSize) is exactly deterministic - no probability
     * or safety margin involved. A shop not actually carrying an item is expressed as
     * itemCount=0 (listed, out of stock) rather than by skipping the row.
     * @param itemCatalogSize the number of distinct items in the catalog (1..itemCatalogSize)
     */
    public List<ShopEntryDto> generateForShop(int shopId, int shopCount, int itemCatalogSize) {
        if (shopId < 1 || shopId > shopCount) {
            throw new IllegalArgumentException("shopId " + shopId + " out of range [1, " + shopCount + "]");
        }
        List<ShopEntryDto> shopEntries = new ArrayList<>(itemCatalogSize);
        for (int itemId = 1; itemId <= itemCatalogSize; itemId++) {
            int itemCount = random.nextInt(maxStockQuantity + 1); // [0, maxStockQuantity], 0 included
            shopEntries.add(new ShopEntryDto(itemId, shopId, itemCount));
        }
        return shopEntries;
    }
}
