package ua.shpp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The capital-letter rule is aimed at item_types.csv, whose every category is a proper noun
 * ("Сантехніка", "Побутова хімія"). A lowercase or space-prefixed entry there is a typo, and
 * without this rule it would reach the database unnoticed. \p{Lu} rather than [A-ZА-Я] so it
 * holds for any alphabet the catalogue might gain later.
 */
public record ItemTypeDto(
        @NotBlank
        @Size(min = 3, max = 255)
        @Pattern(regexp = "^\\p{Lu}.*", message = "must start with a capital letter")
        String name
) { }
