package ua.shpp.generation;

import ua.shpp.dto.ShopEntryDto;

import java.util.List;
import java.util.Random;

public class ShopEntryGenerator {
    private final int invalidRatePercent;
    private final int maxStockQuantity;
    private final Random random;

    public ShopEntryGenerator(int invalidRatePercent, int maxStockQuantity) {
        this(invalidRatePercent, maxStockQuantity, new Random());
    }
    //Constructor for testing, allows injecting a Random instance
    public ShopEntryGenerator(int invalidRatePercent, int maxStockQuantity, Random random) {
        this.invalidRatePercent = invalidRatePercent;
        this.maxStockQuantity = maxStockQuantity;
        this.random = random;
    }

    public List<ShopEntryDto> generateForShop(int shopId, int shopCount, int itemCatalogSize) {
        return null; //todo
    }

}