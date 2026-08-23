package ua.shpp.generation;

import ua.shpp.dto.ItemDto;

import java.util.Random;

/**
 * Generates one Item table entry like ("Товар-N", typeId)
 *  , where N is a sequence number supplied by the caller. And the typeId is the type.
 * With a chance of invalidRatePercent, generates also invalid items.
 */
public class ItemGenerator {
    private static final String NAME_PREFIX = "Товар-";

    private final Random random = new Random();
    private final int invalidRatePercent;

    public ItemGenerator(int invalidRatePercent) {
        this.invalidRatePercent = invalidRatePercent;
    }

    /**
     * @param sequenceNumber suffix for the name, so the caller can guarantee uniqueness across calls
     */
    public ItemDto generate(int sequenceNumber, int typeId) {
        String name = NAME_PREFIX + sequenceNumber;
        return new ItemDto(shouldCorrupt() ? corrupt(name) : name, typeId);
    }

    private boolean shouldCorrupt() {
        return random.nextInt(100) < invalidRatePercent;
    }

    private String corrupt(String name) {
        return switch (random.nextInt(4)) {
            case 0 -> "";                              // @NotBlank
            case 1 -> name.substring(0, 2);            // @Size(min = 3)
            case 2 -> name.toLowerCase();              // @Pattern - no leading capital
            default -> " " + name;                     // @Pattern - starts with a space
        };
    }
}
