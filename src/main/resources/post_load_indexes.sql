CREATE INDEX idx_item_type_name ON ItemType(name);
CREATE INDEX idx_item_type_id ON Item(type_id);
CREATE INDEX idx_shop_entry_count ON ShopEntry(item_id, item_count);
