package it.lsoft.watering.Raspberry;

import org.apache.log4j.Logger;

public class ErrorsHandlerFactory {
    private static final Logger logger = Logger.getLogger(ErrorsHandlerFactory.class);
    private static final String ENV_TEST_MODE = "WATERING_TEST_MODE";

    public static IWateringHandler createErrorsHandler(RealTimeData appData) throws Exception {
        String testMode = System.getenv(ENV_TEST_MODE);
        boolean isTestMode = "true".equalsIgnoreCase(testMode);

        if (isTestMode) {
            logger.info("Creating Mock Error Handler (Test Mode)");
            return new MockErrorsHandler(appData);
        } else {
            logger.info("Creating Pi Error Handler (Production Mode)");
            return new ErrorsHandler(appData);
        }
    }
} 