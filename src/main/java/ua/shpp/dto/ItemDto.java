package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ItemDto(
        @NotBlank
        @Size(min = 3, max = 255)
        @Pattern(regexp = "^\\p{Lu}.*", message = "must start with a capital letter")
        String name,
        @Positive int typeId
) { }
