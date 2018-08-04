package it.lsoft.watering.Raspberry;

import java.util.EnumSet;

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

public class DigitalOuput {
	static final int FRONT_UP = 1;
	static final int FRONT_DOWN = 2;
	private GpioPinDigitalOutput pin;
	private int front = 0;
	private Pin descr;
	private GpioController gpio = GpioFactory.getInstance();

	static Logger logger = Logger.getLogger(DigitalOuput.class);

	public DigitalOuput(String name, int pinNumber, EnumSet<PinMode> mode)
	{
		descr = new PinImpl(RaspiGpioProvider.NAME, pinNumber, name, mode, PinPullResistance.all()); 
		pin = gpio.provisionDigitalOutputPin(descr, name, PinState.LOW);
	}
	
	public void pinHigh()
	{
		if (pin.isHigh())
		{
			front = 0;
		}
		else
		{
			front = FRONT_UP;
		}
	}

	public void pinDown()
	{
		if (pin.isLow())
		{
			front = 0;
		}
		else
		{
			front = FRONT_DOWN;
		}
	}

	public GpioPinDigitalOutput getPin() {
		return pin;
	}

	public void setPin(GpioPinDigitalOutput pin) {
		this.pin = pin;
	}

	public int getFront() {
		return front;
	}

	public void setFront(int front) {
		this.front = front;
	}

	public Pin getDescr() {
		return descr;
	}

	public void setDescr(Pin descr) {
		this.descr = descr;
	}

}
