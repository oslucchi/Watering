package it.lsoft.watering.Raspberry;

import java.util.Random;
import org.apache.log4j.Logger;

public class MockSensorDataHandler extends Thread implements IWateringHandler {
    private static final Logger logger = Logger.getLogger(MockSensorDataHandler.class);
    private final RealTimeData rtData;
    private final Random random = new Random();

    public MockSensorDataHandler(RealTimeData rtData) {
        this.rtData = rtData;
        logger.info("Initialized Mock Sensor Data Handler");
    }

    @Override
    public void run() {
        logger.debug("Mock Sensor Data Handler thread started");
        while (!rtData.isShutDown()) {
            try {
                // Simulate moisture readings between 30-70%
                for (int i = 0; i < rtData.getParms().getNumberOfSensors(); i++) {
                    double moisture = 30.0 + random.nextDouble() * 40.0;
                    rtData.setMoisture(i, moisture);
                    rtData.setSensorReadValue(i, moisture);
                }
                Thread.sleep(rtData.getParms().getSensorReadInterval() * 1000L);
            } catch (InterruptedException e) {
                logger.error("Error in mock sensor handler: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
} 