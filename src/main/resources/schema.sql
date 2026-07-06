DROP TABLE IF EXISTS ShopEntry;
DROP TABLE IF EXISTS Item;
DROP TABLE IF EXISTS Shop;
DROP TABLE IF EXISTS ItemType;

CREATE TABLE ItemType (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE Shop (
    id SERIAL PRIMARY KEY,
    address VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE Item (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type_id INTEGER NOT NULL,
    CONSTRAINT fk_item_type FOREIGN KEY (type_id) REFERENCES ItemType(id) ON DELETE CASCADE
);

CREATE TABLE ShopEntry (
    id SERIAL PRIMARY KEY,
    item_id INTEGER,
    shop_id INTEGER,
    item_count INTEGER NOT NULL,
    CONSTRAINT fk_shop_entry_item FOREIGN KEY (item_id) REFERENCES Item(id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_entry_shop FOREIGN KEY (shop_id) REFERENCES Shop(id) ON DELETE CASCADE,
    CONSTRAINT uq_shop_entry_item_shop UNIQUE (item_id, shop_id)
);

CREATE INDEX idx_item_type_name ON ItemType(name);
CREATE INDEX idx_item_type_id ON Item(type_id);
CREATE INDEX idx_shop_entry_count ON ShopEntry(item_id, item_count);
