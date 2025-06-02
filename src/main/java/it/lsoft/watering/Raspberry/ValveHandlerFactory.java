package it.lsoft.watering.Raspberry;

import org.apache.log4j.Logger;
import it.lsoft.watering.DBUtils.ArchiveData;

public class ValveHandlerFactory {
    private static final Logger logger = Logger.getLogger(ValveHandlerFactory.class);
    private static final String ENV_TEST_MODE = "WATERING_TEST_MODE";

    public static IWateringHandler createHandler(RealTimeData rtData, int valveId, ArchiveData ad) throws Exception {
        String testMode = System.getenv(ENV_TEST_MODE);
        boolean isTestMode = "true".equalsIgnoreCase(testMode);

        if (isTestMode) {
            logger.info("Creating Mock Valve Handler (Test Mode) for valve " + valveId);
            return new MockValveHandler(rtData, valveId, ad);
        } else {
            logger.info("Creating Pi Valve Handler (Production Mode) for valve " + valveId);
            return new ValveHandler(rtData, valveId, ad);
        }
    }
} 