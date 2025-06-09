package it.lsoft.watering.Raspberry;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

public class MockErrorsHandler extends Thread implements IWateringHandler {
    private final RealTimeData rtData;
    private static final Logger logger = Logger.getLogger(MockErrorsHandler.class);
	private final AtomicBoolean initialized = new AtomicBoolean(false);

    public MockErrorsHandler(RealTimeData appData) {
        this.rtData = appData;
        logger.info("Initialized Mock Error Handler");
    }
    
	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}


    @Override
    public void run() {
        logger.debug("Mock Errors Handler thread started");
    	initialized.set(true);	
        while (!rtData.isShutDown()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                logger.error("Error in mock error handler: " + e.getMessage());
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
} 