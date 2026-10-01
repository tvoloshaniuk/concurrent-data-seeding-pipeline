package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ItemTypeDto(
        @NotBlank
        @Size(min = 3)
        @Pattern(regexp = "^\\p{Ll}")
        @Pattern(regexp = ".*[0-9]+.*")
        String name
) { }