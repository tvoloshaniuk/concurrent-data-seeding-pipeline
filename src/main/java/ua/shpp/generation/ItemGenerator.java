package ua.shpp.generation;

import ua.shpp.dto.ItemDto;

import java.util.Random;

/**
 * Builds one catalogue item per call, occasionally a deliberately broken one so the validator
 * downstream has something real to reject - the task asks for validation, and a validator that
 * can never fire proves nothing.
 * <p>
 * Names are "Товар-N" rather than UUIDs precisely so the rules on ItemDto can be meaningful: a
 * UUID starts with a digit and contains dashes, so any rule richer than "not blank, not too
 * short" would reject the valid data itself. The counter keeps names unique for Item(name)
 * UNIQUE without relying on randomness.
 * <p>
 * Lives here rather than inside DataPopulator, next to ShopEntryGenerator, because it now owns
 * real logic - the invalid-generation modes and their mapping onto the DTO's rules - instead of being
 * four lines of string building.
 */
public class ItemGenerator {
    private static final String NAME_PREFIX = "Товар-";

    private final Random random = new Random();
    private final int invalidRatePercent;

    public ItemGenerator(int invalidRatePercent) {
        this.invalidRatePercent = invalidRatePercent;
    }

    /**
     * @param sequenceNumber position among the items accepted so far, so a rejected candidate is
     *                       simply retried at the same number and names stay contiguous
     */
    public ItemDto generate(int sequenceNumber, int typeId) {
        String name = NAME_PREFIX + sequenceNumber;
        return new ItemDto(shouldCorrupt() ? corrupt(name) : name, typeId);
    }

    private boolean shouldCorrupt() {
        return random.nextInt(100) < invalidRatePercent;
    }

    /**
     * Each mode breaks exactly one rule declared on ItemDto, so an invalid item is guaranteed
     * to be rejected rather than accidentally slipping through as valid.
     */
    private String corrupt(String name) {
        return switch (random.nextInt(4)) {
            case 0 -> "";                              // @NotBlank
            case 1 -> name.substring(0, 2);            // @Size(min = 3)
            case 2 -> name.toLowerCase();              // @Pattern - no leading capital
            default -> " " + name;                     // @Pattern - starts with a space
        };
    }
}
