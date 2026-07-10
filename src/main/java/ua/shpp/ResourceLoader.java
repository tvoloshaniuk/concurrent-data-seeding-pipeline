package ua.shpp;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public final class ResourceLoader {
    private ResourceLoader() {
    }

    public static InputStream stream(String fileName) {
        InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new RuntimeException("Resource not found: " + fileName);
        }
        return inputStream;
    }

    public static String readText(String fileName) {
        try (InputStream inputStream = stream(fileName)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + fileName, e);
        }
    }

    public static Properties readProperties(String fileName) {
        try (InputStreamReader reader = new InputStreamReader(stream(fileName), StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read properties: " + fileName, e);
        }
    }
}
