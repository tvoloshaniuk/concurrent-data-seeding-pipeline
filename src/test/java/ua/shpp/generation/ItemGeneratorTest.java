package ua.shpp.generation;

import org.junit.jupiter.api.Test;
import ua.shpp.dto.ItemDto;
import ua.shpp.validation.DtoValidator;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* The two ends of the scale are what make the validator provably useful: at 0 it must never
fire, at 100 it must always fire. Same shape as CitizenDataGenerator.generateRandomPojo(0/100)
in pract3. */
class ItemGeneratorTest {
    private final DtoValidator validator = new DtoValidator();

    @Test
    void generate_producesOnlyValidItemsWhenInvalidGenerationIsOff() {
        ItemGenerator generator = new ItemGenerator(0);

        assertTrue(IntStream.rangeClosed(1, 500)
                .mapToObj(i -> generator.generate(i, 1))
                .allMatch(validator::isValid));
    }

    @Test
    void generate_producesOnlyInvalidItemsWhenInvalidGenerationIsAlwaysOn() {
        ItemGenerator generator = new ItemGenerator(100);

        assertTrue(IntStream.rangeClosed(1, 500)
                .mapToObj(i -> generator.generate(i, 1))
                .noneMatch(validator::isValid));
    }

    @Test
    void generate_namesValidItemsAfterTheirSequenceNumber() {
        ItemGenerator generator = new ItemGenerator(0);

        assertEquals("Товар-42", generator.generate(42, 1).name());
    }

    @Test
    void generate_carriesTheRequestedTypeId() {
        ItemGenerator generator = new ItemGenerator(0);

        ItemDto item = generator.generate(1, 7);

        assertEquals(7, item.typeId());
    }

    /* Names are what Item(name) UNIQUE relies on, so distinct sequence numbers must never
    collide - the reason a counter is used rather than random strings. */
    @Test
    void generate_givesDistinctNamesToDistinctSequenceNumbers() {
        ItemGenerator generator = new ItemGenerator(0);

        long distinct = IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> generator.generate(i, 1).name())
                .distinct()
                .count();
        assertEquals(1000, distinct);
    }
}
