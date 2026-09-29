package ua.shpp.generation;

import java.util.concurrent.ThreadLocalRandom;

public class ItemGenerator {
    private ItemGenerator () {
    }

    public static String generateItemName(int position, int invalidRatePercent) {
        return invalidRatePercent > ThreadLocalRandom.current().nextInt(100)
                ? "Item " + position
                : corruptItem();
    }

    private static String corruptItem() {
        return "Invalid Item";
    }

}