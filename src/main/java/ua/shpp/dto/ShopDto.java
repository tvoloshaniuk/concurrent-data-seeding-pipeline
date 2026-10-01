package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShopDto(
        @NotBlank
        @Size(min = 3)
        @Pattern(regexp = "^\\p{Lu}.*")
        @Pattern(regexp = ".*[0-9]+.*")
        String address
) {
}
