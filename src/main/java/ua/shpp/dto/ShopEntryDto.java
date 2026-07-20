package ua.shpp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

// shopId's upper bound depends on the real shop count read from shops.csv at runtime,
// so it can't be a static @Max — that dynamic range is checked explicitly where shopId
// is generated (see ShopEntryGenerator), not via Bean Validation here.
public record ShopEntryDto(
        @Positive int itemId,
        @Positive int shopId,
        @Min(0) int itemCount
) {

}
