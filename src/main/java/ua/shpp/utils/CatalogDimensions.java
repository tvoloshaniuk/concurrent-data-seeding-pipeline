package ua.shpp.utils;

/**
 * The two numbers DataPopulator settles once the foundation tables are in place, and which the
 * ShopEntry pipeline needs to plan its work: how many shops exist and how many items the catalog
 * holds. Their product is the exact ShopEntry row count, since every item is stocked in every shop.
 */
public record CatalogDimensions(
        int shopCount,
        int itemCatalogSize
) { }
