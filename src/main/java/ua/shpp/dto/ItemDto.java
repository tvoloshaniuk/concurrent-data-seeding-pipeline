package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ItemDto(
        @Positive int id,
        @NotBlank @Size(min = 3, max = 255) String name,
        @Positive int typeId
) {
}
