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

import it.lsoft.watering.Commons.Errors;
import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.DBUtils.ArchiveData;
import it.lsoft.watering.DBUtils.History;

public class ValveHandler extends Thread implements IWateringHandler
{
	final GpioController gpio = GpioFactory.getInstance();
	private GpioPinDigitalOutput pin;
	private Pin pinDescr;

	private RealTimeData rtData;
	private ArchiveData ad;
	private String pinName;
	private int secondsElapsed= 0;
	private int instance;
	private Parameters parms = null;
	private final AtomicBoolean initialized = new AtomicBoolean(false);


	static Logger logger = Logger.getLogger(ValveHandler.class);
	
	public ValveHandler(RealTimeData appData, int instance, ArchiveData ad)
	{
		this.rtData = appData;
		this.parms = rtData.getParms();
		this.instance = instance;
		this.ad = ad;
		
		logger.debug("Creating pin " + parms.getValveGPIOZone(instance));
		pinName = "Zone_" + instance;
		pinDescr = new PinImpl(RaspiGpioProvider.NAME, parms.getValveGPIOZone(instance), pinName, 
				                   EnumSet.of(PinMode.DIGITAL_OUTPUT),
				                   PinPullResistance.all()); 
		pin = gpio.provisionDigitalOutputPin(pinDescr, pinName, 
						(parms.getHighValue() == 1 ? PinState.LOW : PinState.HIGH));
		initialized.set(true);
	}
	
	private void setPinHigh()
	{
		if (parms.getHighValue() == 1)
		{
			pin.high();
		}
		else
		{
			pin.low();
		}
	}

	private void setPinLow()
	{
		if (parms.getHighValue() == 1)
		{
			pin.low();
		}
		else
		{
			pin.high();
		}
	}
	
	private boolean isPinHigh()
	{
		if ((parms.getHighValue() == 1) && pin.isHigh())
		{
			return true;
		}
		else if ((parms.getHighValue() == 0) && pin.isLow())
		{
			return true;
		}
		return false;
	}
	
	
	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}
	
	@Override
	public void run() 
	{
		logger.debug("Valve Handler " + instance + " started");
		boolean stopDueToMoistureLevel = parms.isEnableAutoSkip();

		while(!rtData.isShutDown())
		{
			parms = rtData.getParms();
			
			if (!rtData.getValveStatus(instance))
			{
				rtData.setWateringTimeElapsed(instance, 0);
				secondsElapsed = 0;
			}

			boolean anyMoistureOverMax = false;
			double moistureVal = -1;
			// logger.debug("Valvehandler instance " + instance + ". Incycle " + rtData.getInCycle());
			if (rtData.getInCycle() < 0)
			{
				for(int i = 0; i < parms.getNumberOfSensors(); i++)
				{
					if (parms.isUseMoistureSensor() && 
						(rtData.getMoisture(i) != null) &&
						(rtData.getMoisture(i) > parms.getStopIfMoistureIsGreater()))
					{
						anyMoistureOverMax = true;
						moistureVal = (moistureVal < rtData.getMoisture(i) ? rtData.getMoisture(i) : moistureVal);
					}
				}
			}
			if (stopDueToMoistureLevel != anyMoistureOverMax)
			{
				logger.debug("Moisture level " + moistureVal + 
						 " stop value set to " + parms.getStopIfMoistureIsGreater());
				stopDueToMoistureLevel = anyMoistureOverMax;
				logger.trace("Set stop flag to " + stopDueToMoistureLevel);
			}
			
			// activate only if non in disabled mode or when moisture is less than max configured value
			if (rtData.getValveStatus(instance) && !rtData.isSuspendFlag() && !stopDueToMoistureLevel)
			{
				if (!isPinHigh())
				{
					if (secondsElapsed != 0)
					{
						logger.debug("Valve " + pin.getName() + " required to open is not responding");
						rtData.setErrorCode(rtData.getErrorCode() | Errors.VALVE_HANDLE_ERROR);
					}
					else
					{
						logger.debug("Valve " + pin.getName() + " required to open");
						ad.archive(History.TYPE_VALVE_CHANGE_STATUS, instance, 1);
					}
					setPinHigh();
				}
				else
				{
					rtData.setErrorCode(rtData.getErrorCode() & ~Errors.VALVE_HANDLE_ERROR);
				}
				secondsElapsed++;
				rtData.setWateringTimeElapsed(instance, secondsElapsed);
			}
			else
			{
				if (isPinHigh())
				{
					ad.archive(History.TYPE_VALVE_CHANGE_STATUS, instance, 0);					
				}
				setPinLow();
			}
			try 
			{
				Thread.sleep(1000);
			} 
			catch (InterruptedException e) 
			{
				e.printStackTrace();
			}
		}
		logger.trace("Instance " + instance + " shutting down");
		setPinLow();
	}
}
