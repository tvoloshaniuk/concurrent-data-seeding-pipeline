package ua.shpp.generation;


import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemGeneratorTest {
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void testGenerateItemName(int position) {
        ItemGenerator itemGenerator = new ItemGenerator(0);
        String name = itemGenerator.generateItemName(position);
        assertEquals("Item %d".formatted(position), name);
    }
}