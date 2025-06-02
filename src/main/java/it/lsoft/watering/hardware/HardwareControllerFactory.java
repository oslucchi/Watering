package it.lsoft.watering.hardware;

import it.lsoft.watering.Commons.Parameters;
import org.apache.log4j.Logger;

public class HardwareControllerFactory {
    private static final Logger logger = Logger.getLogger(HardwareControllerFactory.class);
    private static final String ENV_TEST_MODE = "WATERING_TEST_MODE";

    public static IHardwareController createController(Parameters params) {
        String testMode = System.getenv(ENV_TEST_MODE);
        boolean isTestMode = "true".equalsIgnoreCase(testMode);

        if (isTestMode) {
            logger.info("Creating Mock Hardware Controller (Test Mode)");
            return new MockHardwareController(params);
        } else {
            logger.info("Creating Pi Hardware Controller (Production Mode)");
            return new PiHardwareController(params);
        }
    }
} 