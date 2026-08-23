package ua.shpp.utils;

/**
 * ShopEntry pipeline needs to plan its work: how many shops exist and how many items the catalog
 * holds.
 */
public record CatalogDimensions(
        int shopCount,
        int itemCatalogSize
) { }
