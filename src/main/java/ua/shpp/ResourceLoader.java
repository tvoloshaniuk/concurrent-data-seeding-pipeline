package ua.shpp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

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

    public static String loadText(String fileName) {
        try (InputStream inputStream = stream(fileName)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + fileName, e);
        }
    }
}
