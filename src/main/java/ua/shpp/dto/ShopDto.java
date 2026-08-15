package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only @NotBlank and the column bound: addresses are free-form real-world text, so any invented
 * rule (a minimum length, "must contain a digit") risks rejecting a genuinely valid address.
 */
public record ShopDto(
        @NotBlank @Size(max = 255) String address
) { }
