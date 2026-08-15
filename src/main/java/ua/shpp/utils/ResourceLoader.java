package ua.shpp.utils;

import ua.shpp.exceptions.ResourceLoadException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * All-static on purpose, and this is the harmless kind: the class holds no fields, so nothing
 * survives a call and nothing outlives the JVM beyond the class itself. What "static is evil"
 * warns about is static *state* - the ValidatorFactory this project used to keep in a static
 * field, which one application could close out from under another sharing the JVM.
 */
public final class ResourceLoader {
    private ResourceLoader() {
    }

    public static InputStream stream(String fileName) {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new ResourceLoadException("Resource not found: " + fileName);
        }
        return inputStream;
    }

    public static String readText(String fileName) {
        try (InputStream inputStream = stream(fileName)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResourceLoadException("Failed to read resource: " + fileName, e);
        }
    }

    /**
     * Still classpath-based - the Reader wraps the very same stream(fileName) above. It exists
     * for the charset: Properties.load(InputStream) decodes as ISO-8859-1 by legacy contract,
     * which mangles UTF-8, while Properties.load(Reader) honours the reader's charset.
     */
    public static Properties readProperties(String fileName) {
        try (InputStreamReader reader = new InputStreamReader(stream(fileName), StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new ResourceLoadException("Failed to read properties: " + fileName, e);
        }
    }
}
