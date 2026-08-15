package ua.shpp.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

public record ShopEntryDto(
        @Positive int itemId,
        @Positive int shopId,
        @Min(0) int itemCount
) { }
