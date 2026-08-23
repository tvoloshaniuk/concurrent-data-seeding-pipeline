package ua.shpp.generation;

import ua.shpp.dto.ShopEntryDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class ShopEntryGenerator {

    private final Random random = new Random();
    private final int maxStockQuantity; //means ShopEntry.itemCount column
    private final int invalidRatePercent;

    public ShopEntryGenerator(int maxStockQuantity, int invalidRatePercent) {
        this.maxStockQuantity = maxStockQuantity;
        this.invalidRatePercent = invalidRatePercent;
    }

    public List<ShopEntryDto> generateForShop(int shopId, int shopCount, int itemCatalogSize) {
        if (shopId < 1 || shopId > shopCount) {
            throw new IllegalArgumentException("shopId " + shopId + " out of range [1, " + shopCount + "]");
        }
        List<ShopEntryDto> shopEntries = new ArrayList<>(itemCatalogSize);
        for (int itemId = 1; itemId <= itemCatalogSize; itemId++) {
            if (shouldGenerateInvalid()) {
                shopEntries.add(invalidShopEntry(itemId, shopId));
            }
            int itemCount = random.nextInt(maxStockQuantity + 1); // [0, maxStockQuantity], 0 included
            shopEntries.add(new ShopEntryDto(itemId, shopId, itemCount));
        }
        return shopEntries;
    }

    private boolean shouldGenerateInvalid() {
        return random.nextInt(100) < invalidRatePercent;
    }

    //invalid == not an id
    @SuppressWarnings("DataFlowIssue")
    private ShopEntryDto invalidShopEntry(int itemId, int shopId) {
        return random.nextBoolean()
                ? new ShopEntryDto(itemId, shopId, -1)   // @Min(0) on itemCount
                : new ShopEntryDto(0, shopId, 1);        // @Positive on itemId
    }
}
