package it.lsoft.watering.Raspberry;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

public class MockPumpHandler extends Thread implements IWateringHandler {
    private static final Logger logger = Logger.getLogger(MockPumpHandler.class);
    private final RealTimeData rtData;

    public MockPumpHandler(RealTimeData rtData) {
        this.rtData = rtData;
        logger.info("Initialized Mock Pump Handler");
    }
	private final AtomicBoolean initialized = new AtomicBoolean(false);

	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}

    @Override
    public void run() {
        logger.debug("Mock Pump Handler thread started");
    	initialized.set(true);	
        while (!rtData.isShutDown()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error("Error in mock pump handler: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
} 