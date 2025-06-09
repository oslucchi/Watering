package it.lsoft.watering.Raspberry;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiFactory;

import it.lsoft.watering.Commons.Parameters;

public class SensorDataHandler extends Thread implements IWateringHandler
{
	final static int AVERAGE_INDEX = 0;
	private Parameters parms;
	private RealTimeData rtData;
    private static SpiDevice spi = null;
	private static Double[][] lastMeasures = null;
	
	static Logger logger = Logger.getLogger(SensorDataHandler.class);
	private final AtomicBoolean initialized = new AtomicBoolean(false);
	
	public SensorDataHandler(RealTimeData rtData) throws IOException
	{
        this.rtData = rtData;
 		this.parms = rtData.getParms();
 		logger.debug("SPI thread staring. Getting spi instance from factory");
        
        spi = SpiFactory.getInstance(SpiChannel.CS0,
                                     SpiDevice.DEFAULT_SPI_SPEED, // default spi speed 1 MHz
                                     SpiDevice.DEFAULT_SPI_MODE); // default spi mode 0
		logger.trace("got " + spi.toString());
		lastMeasures = new Double[parms.getNumberOfSensors()][parms.getMeasuresToConsider() + 1];
		for(int i = 0; i < parms.getNumberOfSensors(); i++)
		{
			lastMeasures[i][0] = 0.0;
			for(int y = 1; y <= parms.getMeasuresToConsider(); y++)
			{
				lastMeasures[i][y] = null;
			}
		}
			
	}

	private int read(int channel) throws IOException {
        // 10-bit ADC MCP3008
        byte packet[] = new byte[3];
        int readVal = 0;
        packet[0] = 0x01;  // INIT_CMD;  // address byte
        packet[1] = (byte) ((0x08 + channel) << 4);  // singleEnded + channel
        packet[2] = 0x00;
            
        byte[] result = spi.write(packet);
        readVal = ((result[1] & 0x03 ) << 8) | (result[2] & 0xff);
        // logger.debug("Read internal. Value returned " + readVal );
        return readVal;
    }


	public boolean readFromChannel(int arrayIdx)
	{
		int unsignedValue = 0;
		int sensorChannel = parms.getSensorsId()[arrayIdx];
		try 
		{
			unsignedValue = read(sensorChannel);
		}
		catch (IOException e) 
		{
			logger.error("IOEXception " + e.getMessage() + " reading sensor " + arrayIdx);
			return false;
		}
		rtData.setSensorReadValue(arrayIdx, Double.valueOf(unsignedValue));
		rtData.setSensorRangeFrom(arrayIdx, Double.valueOf(parms.getSensorRange(arrayIdx)[0]));
		rtData.setSensorRangeTo(arrayIdx, Double.valueOf(parms.getSensorRange(arrayIdx)[1]));
		return true;
	}
	
	private Double measuresAverage(Double[] values)
	{
		double sum = 0.0;
		int population = 0;
		for(int i = 1; i <= parms.getMeasuresToConsider(); i++)
		{
			if (values[i] != null)
			{
				sum += values[i];
				population++;
			}
		}
		if (population == 0)
		{
			return 0.0;
		}
		else
		{
			return sum / population;
		}
	}

	@Override
	public boolean isInitialized() {
	    return initialized.get();
	}

	@Override
	public void run() 
	{
		int counter = 0;
		int warmUp = parms.getMeasuresToConsider();
		logger.debug("Using " + warmUp + " measures to warmup");
		logger.debug("Sensor Handler thread started");
		initialized.set(true);	
		try 
		{
			while(!rtData.isShutDown())
			{
				parms = rtData.getParms();
				
				if ((counter > parms.getSensorReadInterval()) || (warmUp > 0))
				{
					//Read based on the interval stated in the conf file
					counter = 0;
					for(int i = 0; i < parms.getNumberOfSensors(); i++)
					{
						if (readFromChannel(i))
						{
							if (warmUp > 0)
							{
								lastMeasures[i][warmUp] = rtData.getSensorReadValue(i);
								lastMeasures[i][0] = measuresAverage(lastMeasures[i]); 
								rtData.setSensorReadValue(i, null);
								rtData.setMoisture(i, null);
							}
							else
							{
								for(int y = parms.getMeasuresToConsider(); y > 1; y--)
								{
									lastMeasures[i][y] = lastMeasures[i][y - 1];
								}
								lastMeasures[i][1] = rtData.getSensorReadValue(i);
								lastMeasures[i][0] = measuresAverage(lastMeasures[i]); 
								rtData.setMoisture(i, 
												   100.0 * Math.abs(lastMeasures[i][0] - parms.getSensorRange(i)[1]) / 
												   (parms.getSensorRange(i)[1] - parms.getSensorRange(i)[0]));
							}
							if (parms.isDumpSensorReading())
							{
								logger.info((warmUp > 0 ? "WARMUP PHASE - " : "") + 
										 "Sensor " + i + " - moisture " + 
										 String.format("%4.2f", rtData.getMoisture(i)) + 
										 " [min " + rtData.getSensorRangeFrom(i) + " / " +
										 "max " + rtData.getSensorRangeTo(i) + " / " +
										 "val " + rtData.getSensorReadValue(i) + "]");
							}
						}
					}
				}
				Thread.sleep(1000);
				if (warmUp > 0)
				{
					warmUp--;
				}
				else
				{
					counter++;
				}
			}
		}
		catch (InterruptedException e) 
		{
			;
		}
	}
}
