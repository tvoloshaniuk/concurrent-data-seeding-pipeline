package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * No id field: Item.id is a SERIAL the database assigns on insert, so carrying one here would
 * be a number nothing reads and nothing can keep in sync with the actual row.
 * <p>
 * The name rules describe what a human-entered product name should look like, not what the
 * generator happens to produce - a rule that merely mirrored the generator would only ever
 * confirm that the generator ran. ItemGenerator deliberately violates each of them at
 * invalidRatePercent, which is how the validator gets something real to reject.
 */
public record ItemDto(
        @NotBlank
        @Size(min = 3, max = 255)
        @Pattern(regexp = "^\\p{Lu}.*", message = "must start with a capital letter")
        String name,
        @Positive int typeId
) { }
