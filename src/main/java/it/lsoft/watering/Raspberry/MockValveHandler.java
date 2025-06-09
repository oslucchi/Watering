package it.lsoft.watering.Raspberry;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import it.lsoft.watering.DBUtils.ArchiveData;

public class MockValveHandler extends Thread implements IWateringHandler {
    private static final Logger logger = Logger.getLogger(MockValveHandler.class);
    private final RealTimeData rtData;
    private final int valveId;
    private final ArchiveData ad;

    public MockValveHandler(RealTimeData rtData, int valveId, ArchiveData ad) {
        this.rtData = rtData;
        this.valveId = valveId;
        this.ad = ad;
        logger.info("Initialized Mock Valve Handler for valve " + valveId);
    }
	private final AtomicBoolean initialized = new AtomicBoolean(false);

	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}

    @Override
    public void run() {
        logger.debug("Mock Valve Handler thread started for valve " + valveId);
        int elapsed = 0;
    	initialized.set(true);	
        while (!rtData.isShutDown()) {
            try {
                if (rtData.getValveStatus(valveId)) {
                    elapsed++;
                    rtData.setWateringTimeElapsed(valveId, elapsed);
                    // Simulate moisture increase when valve is open
                    if (rtData.getParms().getSensorIdPerArea()[valveId] >= 0) {
                        int sensorId = rtData.getParms().getSensorIdPerArea()[valveId];
                        Double currentMoisture = rtData.getMoisture(sensorId);
                        if (currentMoisture != null) {
                            rtData.setMoisture(sensorId, Math.min(currentMoisture + 0.5, 100.0));
                        }
                    }
                } else {
                    elapsed = 0;
                    rtData.setWateringTimeElapsed(valveId, 0);
                }
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error("Error in mock valve handler: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
} 