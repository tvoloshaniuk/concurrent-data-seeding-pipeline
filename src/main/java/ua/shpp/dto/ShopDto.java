package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShopDto(
        @NotBlank @Size(max = 255) String address
) {
}
