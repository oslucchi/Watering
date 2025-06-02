package it.lsoft.watering.hardware;

import org.apache.log4j.Logger;
import it.lsoft.watering.Commons.Parameters;
import java.util.Arrays;
import java.util.Random;

public class MockHardwareController implements IHardwareController {
    private final boolean[] valveStatus;
    private boolean pumpStatus;
    private final Logger logger = Logger.getLogger(MockHardwareController.class);
    private String errorMessage = null;
    private final Random random = new Random();
    private final int[] mockMoistureValues;
    private final int[] mockSensorValues;

    public MockHardwareController(Parameters params) {
        this.valveStatus = new boolean[params.getZones()];
        this.mockMoistureValues = new int[params.getNumberOfSensors()];
        this.mockSensorValues = new int[params.getNumberOfSensors()];
        
        // Initialize with random values
        for (int i = 0; i < params.getNumberOfSensors(); i++) {
            mockMoistureValues[i] = 30 + random.nextInt(40); // Random values between 30-70%
            mockSensorValues[i] = 300 + random.nextInt(400); // Random sensor values
        }
        
        logger.info("Mock hardware controller initialized with " + params.getZones() + " zones");
    }

    @Override
    public void setValveStatus(int valveIndex, boolean status) {
        if (valveIndex >= 0 && valveIndex < valveStatus.length) {
            valveStatus[valveIndex] = status;
            logger.debug("Mock: Valve " + valveIndex + " set to " + status);
            
            // Simulate moisture changes when valve is on/off
            if (valveIndex < mockMoistureValues.length) {
                if (status) {
                    // Increase moisture when valve is on
                    mockMoistureValues[valveIndex] = Math.min(100, mockMoistureValues[valveIndex] + 5);
                } else {
                    // Decrease moisture when valve is off
                    mockMoistureValues[valveIndex] = Math.max(20, mockMoistureValues[valveIndex] - 2);
                }
            }
        }
    }

    @Override
    public void setPumpStatus(boolean status) {
        this.pumpStatus = status;
        logger.debug("Mock: Pump set to " + status);
    }

    @Override
    public Integer readMoisture(int sensorId) {
        if (sensorId >= 0 && sensorId < mockMoistureValues.length) {
            return mockMoistureValues[sensorId];
        }
        return null;
    }

    @Override
    public Integer readSensorValue(int sensorId) {
        if (sensorId >= 0 && sensorId < mockSensorValues.length) {
            return mockSensorValues[sensorId];
        }
        return null;
    }

    @Override
    public void shutdown() {
        Arrays.fill(valveStatus, false);
        pumpStatus = false;
        logger.info("Mock: Hardware shutdown completed");
    }

    @Override
    public boolean isError() {
        return errorMessage != null;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    // Additional methods for testing
    public boolean getValveStatus(int valveIndex) {
        return valveIndex >= 0 && valveIndex < valveStatus.length ? valveStatus[valveIndex] : false;
    }

    public boolean getPumpStatus() {
        return pumpStatus;
    }

    public void simulateError(String message) {
        this.errorMessage = message;
        logger.error("Mock: Simulated error - " + message);
    }

    public void clearError() {
        this.errorMessage = null;
    }
} 