package it.lsoft.watering.hardware;

import com.pi4j.io.gpio.*;
import org.apache.log4j.Logger;
import it.lsoft.watering.Commons.Parameters;

public class PiHardwareController implements IHardwareController {
    private final GpioController gpio;
    private final GpioPinDigitalOutput[] valvePins;
    private final GpioPinDigitalOutput pumpPin;
    private final Parameters params;
    private final Logger logger = Logger.getLogger(PiHardwareController.class);
    private String errorMessage = null;

    public PiHardwareController(Parameters params) {
        this.params = params;
        this.gpio = GpioFactory.getInstance();
        this.valvePins = new GpioPinDigitalOutput[params.getZones()];
        
        try {
            // Initialize valve pins
            for (int i = 0; i < params.getZones(); i++) {
                Pin pin = RaspiPin.getPinByAddress(params.getValveGPIOZone(i));
                valvePins[i] = gpio.provisionDigitalOutputPin(
                    pin,
                    "Valve_" + i,
                    PinState.LOW
                );
            }

            // Initialize pump pin
            Pin pumpGpioPin = RaspiPin.getPinByAddress(params.getPumpGPIO());
            pumpPin = gpio.provisionDigitalOutputPin(
                pumpGpioPin,
                "Pump",
                PinState.LOW
            );
        } catch (Exception e) {
            logger.error("Error initializing GPIO: " + e.getMessage());
            throw new RuntimeException("Failed to initialize GPIO", e);
        }
    }

    @Override
    public void setValveStatus(int valveIndex, boolean status) {
        try {
            if (valveIndex >= 0 && valveIndex < valvePins.length) {
                valvePins[valveIndex].setState(status ? PinState.HIGH : PinState.LOW);
                logger.debug("Valve " + valveIndex + " set to " + status);
            }
        } catch (Exception e) {
            errorMessage = "Error setting valve status: " + e.getMessage();
            logger.error(errorMessage);
        }
    }

    @Override
    public void setPumpStatus(boolean status) {
        try {
            pumpPin.setState(status ? PinState.HIGH : PinState.LOW);
            logger.debug("Pump set to " + status);
        } catch (Exception e) {
            errorMessage = "Error setting pump status: " + e.getMessage();
            logger.error(errorMessage);
        }
    }

    @Override
    public Integer readMoisture(int sensorId) {
        // Implement actual moisture reading logic here
        try {
            // This would use ADC or other sensor reading logic
            return null; // Replace with actual implementation
        } catch (Exception e) {
            errorMessage = "Error reading moisture: " + e.getMessage();
            logger.error(errorMessage);
            return null;
        }
    }

    @Override
    public Integer readSensorValue(int sensorId) {
        // Implement actual sensor reading logic here
        try {
            // This would use ADC or other sensor reading logic
            return null; // Replace with actual implementation
        } catch (Exception e) {
            errorMessage = "Error reading sensor value: " + e.getMessage();
            logger.error(errorMessage);
            return null;
        }
    }

    @Override
    public void shutdown() {
        try {
            gpio.shutdown();
        } catch (Exception e) {
            errorMessage = "Error during shutdown: " + e.getMessage();
            logger.error(errorMessage);
        }
    }

    @Override
    public boolean isError() {
        return errorMessage != null;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }
} 