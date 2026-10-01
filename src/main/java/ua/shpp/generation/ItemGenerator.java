package ua.shpp.generation;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class ItemGenerator {

    private final Random random = new Random();
    private final int invalidRatePercent;

    public ItemGenerator (int invalidRatePercent) {
        this.invalidRatePercent = invalidRatePercent;
    }

    public String generateItemName(int position) {
        return shouldCorrupt() ? corruptItem(position) : "Item %d".formatted(position);
    }

    private boolean shouldCorrupt() {
        return invalidRatePercent > random.nextInt(100);
    }

    private static String corruptItem(int position) {
        return switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> ""; // @NotBlank
            case 1 -> "I%d".formatted(position); // @Size(min = 3)
            case 2 -> "invalid Item %d".formatted(position); // @Pattern(regexp = "^\\p{Lu}.*") - no leading capital
            case 3 -> "invalid Item"; // @Pattern(regexp = ".*[0-9]+.*") - no digits
            default -> throw new IllegalStateException();
        };
    }

}
