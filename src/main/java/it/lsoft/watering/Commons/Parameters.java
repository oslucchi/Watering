package it.lsoft.watering.Commons;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;

import it.lsoft.watering.Raspberry.RealTimeData;

/**
 * @author osvaldo
 *
 */
public class Parameters 
{
	static Logger logger = Logger.getLogger(Parameters.class);

	private int zones;
	private int[] durationZone;
	private int stopIfMoistureIsGreater;
	private int numberOfSensors;
	private String persistFilePath;
	private int archiveEvery;
	private int[] valveGPIOZone;
	private int[] sensorsId;
	private int sensorReadInterval;
	private int sensorValueDumpInterval;
	private int[] errorLedGPIO = new int[3];
	private int[][] sensorRange;
	private int adcBus = 0x1;
	private int adcAddress = 0x40;
	private int pumpGPIO;
	private String dbHost;
	private int blinkLight = 500; 
	private int blinkPause = 2000; 
	private boolean enableDbArchive = false;
	private boolean enablePump = false;
	private boolean useMoistureSensor = true;
	private boolean adminViaSocket = true;
	private int adminPort;
	private int highValue = 1;
	private int resetBtn;
	private String[] schedule;
	private int[][][] durations;
	private boolean[] activeSchedules;
	private double extendBy = 1.0;
	private int measuresToConsider = 6;
	private double[] skipTreshold;
	private double[] expectedMoistureAfterWatering;
	private boolean enableAutoSkip;
	private boolean dumpSensorReading;
	private int[] sensorIdPerArea;
	
	private static Parameters instance = null;
	private static String confFilePath;
	
	public static Parameters getInstance(String pConfFilePath) //throws InvalidFileFormatException, IOException
	{
		confFilePath = pConfFilePath;
		if (instance == null)
			instance = new Parameters(confFilePath);
		return instance;
	}
	
	public Parameters rescan() throws InvalidFileFormatException, IOException
	{
		instance = new Parameters(confFilePath);
		return instance;
	}

	private Parameters(String pConfFilePath) // throws InvalidFileFormatException, IOException
	{
	    // Inizializza la libreria di log
		// PropertyConfigurator.configure("/Watering.ini");
		
	    // Leggere i Parametri con cui gira questa istanza dell'applicazione
		Ini ini = null;
		try 
		{
			ini = new Ini(new File(confFilePath));
		}
		catch (IOException e) 
		{
			logger.fatal("Exception " + e.getMessage() + " on Params Ini. Aborting");
			System.exit(-1);
		}
		
		zones = Integer.parseInt(ini.get("general", "zones"));
		durationZone = new int [zones];
		valveGPIOZone = new int[zones];
		String scheduleString = ini.get("timer", "schedule");
		schedule = scheduleString.split("\\|");
		durations = new int[zones][schedule.length][7];
		int counter;
		for (int i = 0; i < zones; i++)
		{
			String entryName = "durationZone_" + i;
			// durationZone[i] = Integer.parseInt(ini.get("general", entryName));
			counter = 0;
			for(String item : ini.get("timer", entryName).split("\\|"))
			{
				int day = 0;
				for(String duration : item.split(","))
				{
					durations[i][counter][day++] = Integer.parseInt(duration);
				}
				counter++;
			}
			entryName = "valveGPIOZone_" + i;
			valveGPIOZone[i] = Integer.parseInt(ini.get("io", entryName));
		}
		activeSchedules = new boolean[schedule.length];
		counter = 0;
		for(String active : ini.get("timer", "activeSchedules").split(","))
		{
			activeSchedules[counter++] = Integer.parseInt(active) != 0;
		}
		extendBy = Double.parseDouble(ini.get("timer", "extendBy"));
		
		enableDbArchive = Boolean.parseBoolean(ini.get("general", "enableDbArchive"));
		enablePump = Boolean.parseBoolean(ini.get("general", "enablePump"));
		useMoistureSensor = Boolean.parseBoolean(ini.get("general", "useMoistureSensor"));
		adminViaSocket = Boolean.parseBoolean(ini.get("general", "adminViaSocket"));
		stopIfMoistureIsGreater = Integer.parseInt(ini.get("general", "stopIfMoistureIsGreater"));
		highValue = Integer.parseInt(ini.get("general", "highValue"));
		measuresToConsider = Integer.parseInt(ini.get("general", "measuresToConsider"));

		numberOfSensors = Integer.parseInt(ini.get("io", "numberOfSensors"));
		sensorRange = new int [numberOfSensors][2];
		sensorsId = new int [numberOfSensors];
		adcAddress = Integer.decode(ini.get("io", "adcAddress"));
		adcBus = Integer.decode(ini.get("io", "adcBus"));
		pumpGPIO = Integer.parseInt(ini.get("io", "pumpGPIO"));
		blinkLight = Integer.parseInt(ini.get("io", "blinkLight"));
		blinkPause = Integer.parseInt(ini.get("io", "blinkPause"));
		sensorReadInterval = Integer.parseInt(ini.get("io", "sensorReadInterval"));
		sensorValueDumpInterval = Integer.parseInt(ini.get("io", "sensorValueDumpInterval"));
		for (int i = 0; i < numberOfSensors; i++)
		{
			String entryName = "sensorRange_" + i;
			String range = ini.get("io", entryName);
			StringTokenizer st = new StringTokenizer(range);
			sensorRange[i][0] = Integer.parseInt(st.nextToken());
			sensorRange[i][1] = Integer.parseInt(st.nextToken());
			entryName = "sensorsId_" + i;
			sensorsId[i] = Integer.parseInt(ini.get("io", entryName));
		}
		for(int i = 0; i < errorLedGPIO.length; i++)
		{
			String entryName = "errorLedGPIO_" + i;
			errorLedGPIO[i] = Integer.parseInt(ini.get("io", entryName));
		}

		persistFilePath = ini.get("persistance", "persistFilePath");
		archiveEvery = Integer.parseInt(ini.get("persistance", "archiveEvery"));
		dbHost = ini.get("persistance", "dbHost");
		adminPort = Integer.parseInt(ini.get("admin", "adminPort"));
		resetBtn = Integer.parseInt(ini.get("io", "resetBtn"));

		skipTreshold = new double[numberOfSensors];
		expectedMoistureAfterWatering = new double[numberOfSensors];
		String dummyStringArray = ini.get("general", "skipThreshold");
		String[] dummyStringItems = dummyStringArray.split("\\|");
		for(int i = 0; i < dummyStringItems.length; i++)
		{
			skipTreshold[i] = Double.parseDouble(dummyStringItems[i]);
		}
		dummyStringArray = ini.get("general", "expectedMoistureAfterWatering");
		dummyStringItems = dummyStringArray.split("\\|");
		for(int i = 0; i < dummyStringItems.length; i++)
		{
			expectedMoistureAfterWatering[i] = Double.parseDouble(dummyStringItems[i]);
		}
		enableAutoSkip = Boolean.parseBoolean(ini.get("general", "enableAutoSkip"));
		dumpSensorReading = Boolean.parseBoolean(ini.get("general", "dumpSensorReading"));
		dummyStringArray = ini.get("general", "sensorIdPerArea");
		dummyStringItems = dummyStringArray.split(",");
		sensorIdPerArea = new int [zones];
		for(int i = 0; i < dummyStringItems.length; i++)
		{
			sensorIdPerArea[i] = Integer.parseInt(dummyStringItems[i]);
		}
	}

	public int getZones() {
		return zones;
	}

	public int[] getDurationZone() {
		return durationZone;
	}

	public int getNumberOfSensors() {
		return numberOfSensors;
	}

	public String getPersistFilePath() {
		return persistFilePath;
	}

	public int getArchiveEvery() {
		return archiveEvery;
	}

	public int[] getSensorsId() {
		return sensorsId;
	}

	public int getSensorReadInterval() {
		return sensorReadInterval;
	}
	

	public int getSensorValueDumpInterval() {
		return sensorValueDumpInterval;
	}

	public int getErrorLedGPIO(int i) {
		return errorLedGPIO[i];
	}

	public int[][] getSensorRange() {
		return sensorRange;
	}

	public int getAdcBus() {
		return adcBus;
	}

	public int getAdcAddress() {
		return adcAddress;
	}

	public int getPumpGPIO() {
		return pumpGPIO;
	}

	public String getDbHost() {
		return dbHost;
	}

	public int getBlinkLight() {
		return blinkLight;
	}

	public int getBlinkPause() {
		return blinkPause;
	}

	public boolean isEnableDbArchive() {
		return enableDbArchive;
	}

	public boolean isEnablePump() {
		return enablePump;
	}

	public int getStopIfMoistureIsGreater() {
		return stopIfMoistureIsGreater;
	}

	public boolean isUseMoistureSensor() {
		return useMoistureSensor;
	}

	public int[] getSensorRange(int sensorId) {
		return sensorRange[sensorId];
	}

	public int getValveGPIOZone(int id) {
		return valveGPIOZone[id];
	}

	public boolean isAdminViaSocket() {
		return adminViaSocket;
	}

	public int getAdminPort() {
		return adminPort;
	}

	public int getHighValue() {
		return highValue;
	}

	public int getResetBtn() {
		return resetBtn;
	}

	public String[] getSchedule() {
		return schedule;
	}

	public int[][][] getDurations() {
		return durations;
	}
	
	public int getDuration(RealTimeData rtData, int dayOfTheWeek)
	{
		return (int) (durations[rtData.getInCycle()][rtData.getNextStartIdx()][dayOfTheWeek] * 60 * extendBy);
	}
	
	public int getNumberOfSchedules()
	{
		return schedule.length;
	}

	public boolean[] getActiveSchedules() {
		return activeSchedules;
	}

	public double getExtendBy() {
		return extendBy;
	}

	public int getMeasuresToConsider() {
		return measuresToConsider;
	}

	public double[] getSkipTreshold() {
		return skipTreshold;
	}

	public double[] getExpectedMoistureAfterWatering() {
		return expectedMoistureAfterWatering;
	}

	public boolean isEnableAutoSkip() {
		return enableAutoSkip;
	}

	public void setEnableAutoSkip(boolean enableAutoSkip) {
		this.enableAutoSkip = enableAutoSkip;
	}

	public boolean isDumpSensorReading() {
		return dumpSensorReading;
	}

	public void setDumpSensorReading(boolean dumpSensorReading) {
		this.dumpSensorReading = dumpSensorReading;
	}

	public int[] getSensorIdPerArea() {
		return sensorIdPerArea;
	}
}
