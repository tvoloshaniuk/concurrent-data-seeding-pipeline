package ua.shpp.dto;

import jakarta.validation.constraints.Positive;

public record ItemDto(
        String name,
        @Positive int typeId
) { }
