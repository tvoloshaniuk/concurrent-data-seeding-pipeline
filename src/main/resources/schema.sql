


CREATE TABLE Shop
(
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE ItemType
(
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

CREATE TABLE Item
(
    id      SERIAL PRIMARY KEY,
    type_id INT  NOT NULL,
    name    TEXT NOT NULL UNIQUE,
    CONSTRAINT fk_item_type FOREIGN KEY (type_id) REFERENCES ItemType (id) ON DELETE CASCADE

);

CREATE TABLE ShopEntry
(
    id         SERIAL PRIMARY KEY,
    item_id    INT NOT NULL,
    shop_id    INT NOT NULL,
    item_count INT NOT NULL,
    CONSTRAINT fk_shop_entry_item FOREIGN KEY (item_id) REFERENCES Item (id) ON DELETE CASCADE,
    CONSTRAINT fk_shop_entry_shop FOREIGN KEY (shop_id) REFERENCES Shop (id) ON DELETE CASCADE,
    CONSTRAINT uq_shop_entry_item_shop UNIQUE (item_id, shop_id)
);

