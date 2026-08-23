package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShopDto(
        @NotBlank
        @Size(max = 255)
        @Pattern(regexp = "^\\p{Lu}.*", message = "must start with a capital letter")
        String address
) { }
