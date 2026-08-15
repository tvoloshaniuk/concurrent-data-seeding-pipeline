package ua.shpp.utils;

import org.junit.jupiter.api.Test;
import ua.shpp.exceptions.ResourceLoadException;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The stream under test is asserted on, not consumed - closing it would be the test's only act.
@SuppressWarnings("resource")
class ResourceLoaderTest {
    private static final String MISSING = "definitely-not-on-the-classpath.txt";

    @Test
    void stream_opensAnExistingClasspathResource() throws Exception {
        try (InputStream stream = ResourceLoader.stream("shops.csv")) {
            assertNotNull(stream);
            assertTrue(stream.readAllBytes().length > 0);
        }
    }

    /* Failing loudly here is deliberate: a missing resource means the jar was packaged wrong,
    and a null stream would surface much later as an unrelated NullPointerException. */
    @Test
    void stream_throwsNamingTheResourceThatIsMissing() {
        ResourceLoadException thrown = assertThrows(ResourceLoadException.class, () -> ResourceLoader.stream(MISSING));

        assertTrue(thrown.getMessage().contains(MISSING), thrown.getMessage());
    }

    @Test
    void readText_returnsWholeFileContent() {
        String schema = ResourceLoader.readText("schema.sql");

        assertTrue(schema.contains("CREATE TABLE ShopEntry"), schema);
    }

    @Test
    void readText_throwsForMissingResource() {
        assertThrows(ResourceLoadException.class, () -> ResourceLoader.readText(MISSING));
    }

    @Test
    void readProperties_parsesKeysFromFile() {
        Properties properties = ResourceLoader.readProperties("config.properties");

        assertNotNull(properties.getProperty("db.url"));
        assertNotNull(properties.getProperty("batch.size"));
    }

    @Test
    void readProperties_throwsForMissingResource() {
        assertThrows(ResourceLoadException.class, () -> ResourceLoader.readProperties(MISSING));
    }
}
