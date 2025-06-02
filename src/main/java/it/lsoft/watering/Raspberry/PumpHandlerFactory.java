package it.lsoft.watering.Raspberry;

import org.apache.log4j.Logger;

public class PumpHandlerFactory {
    private static final Logger logger = Logger.getLogger(PumpHandlerFactory.class);
    private static final String ENV_TEST_MODE = "WATERING_TEST_MODE";

    public static IWateringHandler createHandler(RealTimeData rtData) throws Exception {
        String testMode = System.getenv(ENV_TEST_MODE);
        boolean isTestMode = "true".equalsIgnoreCase(testMode);

        if (isTestMode) {
            logger.info("Creating Mock Pump Handler (Test Mode)");
            return new MockPumpHandler(rtData);
        } else {
            logger.info("Creating Pi Pump Handler (Production Mode)");
            return new PumpHandler(rtData);
        }
    }
} 