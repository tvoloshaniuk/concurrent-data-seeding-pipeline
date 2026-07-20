package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ItemTypeDto(
        @NotBlank @Size(max = 255) String name
) {
}
