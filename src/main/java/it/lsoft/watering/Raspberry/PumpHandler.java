package it.lsoft.watering.Raspberry;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.impl.PinImpl;

import it.lsoft.watering.Commons.Parameters;

public class PumpHandler extends Thread implements IWateringHandler
{
	final GpioController gpio = GpioFactory.getInstance();
	private GpioPinDigitalOutput pin;
	private Pin pinDescr;

	private RealTimeData rtData;
	private Parameters parms;
	private String pinName;
	
	static Logger logger = Logger.getLogger(PumpHandler.class);
	private final AtomicBoolean initialized = new AtomicBoolean(false);

	public PumpHandler(RealTimeData rtData)
	{
		this.rtData = rtData;
		this.parms = rtData.getParms();
		
		logger.debug("Setting pin for pump handler. GPIO " + parms.getPumpGPIO());
		pinName = "Pump";
		pinDescr = new PinImpl(RaspiGpioProvider.NAME, parms.getPumpGPIO(), pinName, 
					               EnumSet.of(PinMode.DIGITAL_OUTPUT),
					               PinPullResistance.all()); 

		pin = gpio.provisionDigitalOutputPin(pinDescr, pinName, PinState.HIGH);
	}

	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}

	@Override
	public void run() 
	{
		logger.debug("Pump Handler thread started");
		int i = 0;
		boolean printDebug = true;
		initialized.set(true);	

		while(!rtData.isShutDown())
		{
			parms = rtData.getParms();

			for(i = 0; i < parms.getNumberOfSensors(); i++)
			{
				if (rtData.getValveStatus(i))
				{
					logger.trace("Valve " + i + " is open.");
					break;
				}
			}
			if (i >= parms.getNumberOfSensors())
			{
				logger.trace("No valve opened. Stopping the pump");
				printDebug = false;
				pin.high();
			}
			else
			{
				if (!rtData.isSuspendFlag())
				{
					if (printDebug)
					{
						logger.debug("At least one valve is opene. Starting the pump");
						printDebug = false;
					}
					pin.low();
				}
			}
			
			try 
			{
				Thread.sleep(200);
			} 
			catch (InterruptedException e) 
			{
				;
			}
		}
	}
}
