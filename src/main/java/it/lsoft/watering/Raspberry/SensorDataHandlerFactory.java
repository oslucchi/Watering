package it.lsoft.watering.Raspberry;

import org.apache.log4j.Logger;

public class SensorDataHandlerFactory {
    private static final Logger logger = Logger.getLogger(SensorDataHandlerFactory.class);
    private static final String ENV_TEST_MODE = "WATERING_TEST_MODE";

    public static IWateringHandler createHandler(RealTimeData rtData) throws Exception {
        String testMode = System.getenv(ENV_TEST_MODE);
        boolean isTestMode = "true".equalsIgnoreCase(testMode);

        if (isTestMode) {
            logger.info("Creating Mock Sensor Data Handler (Test Mode)");
            return new MockSensorDataHandler(rtData);
        } else {
            logger.info("Creating Pi Sensor Data Handler (Production Mode)");
            return new SensorDataHandler(rtData);
        }
    }
} 