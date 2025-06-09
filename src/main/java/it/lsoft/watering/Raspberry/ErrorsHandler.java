package it.lsoft.watering.Raspberry;

import it.lsoft.watering.Commons.Parameters;

import java.util.Date;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiGpioProvider;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.impl.PinImpl;

public class ErrorsHandler extends Thread implements IWateringHandler
{
	private GpioController gpio;
	private GpioPinDigitalOutput pinLed1;
	private GpioPinDigitalOutput pinLed2;
	private GpioPinDigitalOutput pinLed3;
	private GpioPinDigitalInput  pinReset;
	private Pin pinDescr;

	private Parameters parms;
	private RealTimeData rtData;
	private String pinName;
	private Date pressStart; 

	static Logger logger = Logger.getLogger(ErrorsHandler.class);
	private final AtomicBoolean initialized = new AtomicBoolean(false);


	public ErrorsHandler(RealTimeData appData) throws Exception
	{
		gpio = GpioFactory.getInstance();
		this.rtData = appData;
		this.parms = rtData.getParms();
		
		logger.debug("Setting pins for error out");
		logger.debug("LED 1 - pin " + parms.getErrorLedGPIO(0));
		logger.debug("LED 2 - pin " + parms.getErrorLedGPIO(1));
		logger.debug("LED 3 - pin " + parms.getErrorLedGPIO(2));
		logger.debug("RESET - pin " + parms.getResetBtn());

		pinName = "LED_";
		pinDescr = new PinImpl(RaspiGpioProvider.NAME, parms.getErrorLedGPIO(0), pinName + "0", 
					               EnumSet.of(PinMode.DIGITAL_OUTPUT),
					               PinPullResistance.all()); 
		pinLed1 = gpio.provisionDigitalOutputPin(pinDescr, pinName, PinState.LOW);
		pinDescr = new PinImpl(RaspiGpioProvider.NAME, parms.getErrorLedGPIO(1), pinName + "1", 
					               EnumSet.of(PinMode.DIGITAL_OUTPUT),
					               PinPullResistance.all()); 
		pinLed2 = gpio.provisionDigitalOutputPin(pinDescr, pinName, PinState.LOW);
		pinDescr = new PinImpl(RaspiGpioProvider.NAME, parms.getErrorLedGPIO(2), pinName + "2", 
					               EnumSet.of(PinMode.DIGITAL_OUTPUT),
					               PinPullResistance.all()); 
		pinLed3 = gpio.provisionDigitalOutputPin(pinDescr, pinName, PinState.LOW);

		// provision gpio pin #02 as an input pin with its internal pull down resistor enabled 
		pinDescr = new PinImpl(RaspiGpioProvider.NAME, parms.getResetBtn(), "reset", 
	               EnumSet.of(PinMode.DIGITAL_INPUT),
	               PinPullResistance.all()); 
		pinReset = gpio.provisionDigitalInputPin(pinDescr, PinPullResistance.PULL_UP); 
		// create and register gpio pin listener 
		pinReset.addListener(
			new GpioPinListenerDigital() 
			{
				@Override public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) 
				{ 
					// display pin state on console 
					if (event.getState() == PinState.LOW)
					{
						logger.debug("Reset button pressed: " + event.getPin() + " = " + event.getState()); 
						if (pressStart == null) 
							pressStart = new Date();
					}
					else if (pressStart != null)
					{
						logger.debug("Reset button released: " + event.getPin() + " = " + event.getState()); 
						logger.debug("It was pressed for " + (new Date().getTime() - pressStart.getTime()) + " ms");
						if (new Date().getTime() - pressStart.getTime() > parms.getPressTimeToStartManual())
						{
							logger.debug("Forcing a manual cycle to start ms");
							rtData.setForceManual(true);
							rtData.setScheduleIndex(0);
						}
						else
						{
							logger.debug("Button pressed for less than " + parms.getPressTimeToStartManual() + 
										 ". Just resetting errors");
							rtData.setErrorCode(rtData.getErrorCode() & 0xFFFF0000);
						}
						pressStart = null;
					}
					else
					{
						rtData.setErrorCode(rtData.getErrorCode() & 0xFFFF0000);
					}
				} 
			}
		);
	}
	
	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}

	@Override
	public void run() 
	{
		logger.debug("Errors Handler thread started");
		byte[] countFlash = {0, 0, 0};
		byte[] countOff = {0, 0, 0};
		long errCode = 0;
		initialized.set(true);	
		while(!rtData.isShutDown())
		{	
			parms = rtData.getParms();
			if (errCode != rtData.getErrorCode())
			{
				logger.debug("New Error been set " + (rtData.getErrorCode() & 0xFF) + " - " +
							 (rtData.getErrorCode() & 0xFF00) + " - " +
							 (rtData.getErrorCode() & 0x00FF0000));
				errCode = rtData.getErrorCode();
			}
			
			if ((rtData.getErrorCode() & 0xFF) != 0)
			{
				if (countFlash[0] <= 0)
				{
					countFlash[0] = (byte) ((rtData.getErrorCode() & 0x000000FF) * 2);
					pinLed1.low();
					countOff[0] = (byte) (parms.getBlinkPause() / parms.getBlinkLight() - 1);
				}
				else
				{
					if (countOff[0] > 0)
					{
						countOff[0]--;
					}
					else
					{
						countFlash[0]--;
						pinLed1.toggle();
					}
				}
			}
			else
			{
				pinLed1.low();
				countFlash[0] = 0;
			}

			if ((rtData.getErrorCode() & 0xFF00) != 0)
			{
				if (countFlash[1] <= 0)
				{
					countFlash[1] = (byte) (((rtData.getErrorCode() & 0x0000FF00) >> 8) * 2);
					pinLed2.low();
					countOff[1] = (byte) (parms.getBlinkPause() / parms.getBlinkLight() - 1);
				}
				else
				{
					if (countOff[1] > 0)
					{
						countOff[1]--;
					}
					else
					{
						countFlash[1]--;
						pinLed2.toggle();
					}
				}
			}
			else
			{
				pinLed2.low();
				countFlash[1] = 0;
			}

			if ((rtData.getErrorCode() & 0x00FF0000) != 0)
			{
				if (countFlash[2] <= 0)
				{
					countFlash[2] = (byte) (((rtData.getErrorCode() & 0x00FF0000) >> 16) * 2);
					pinLed3.low();
					countOff[2] = (byte) (parms.getBlinkPause() / parms.getBlinkLight() - 1);
				}
				else
				{
					if (countOff[2] > 0)
					{
						countOff[2]--;
					}
					else
					{
						countFlash[2]--;
						pinLed3.toggle();
					}
				}
			}
			else
			{
				pinLed3.low();
				countFlash[2] = 0;
			}

			try 
			{
				Thread.sleep(parms.getBlinkLight());
			} 
			catch (InterruptedException e) 
			{
				;
			}
		}
		logger.debug("Shutting down");
	}
}