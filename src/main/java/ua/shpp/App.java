package ua.shpp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.shpp.config.AppConfig;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        AppConfig config = AppConfig.load(args);
        try {
            new EpicenterSeedingService(config).execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while running the pipeline", e);
        } catch (Exception e) {
            log.error("Unexpected error occurred", e);
        }
    }
}
