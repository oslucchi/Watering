package it.lsoft.watering;

import java.text.ParseException;
import java.text.SimpleDateFormat;
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Errors;
import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.DBUtils.ArchiveData;
import it.lsoft.watering.DBUtils.History;
//import it.lsoft.watering.DBUtils.History;
import it.lsoft.watering.Raspberry.*;

public class Watering {
	private static RealTimeData rtData = null;
	private static IWateringHandler errorsHandler = null;
	private static IWateringHandler sensorHandler = null;
	private static IWateringHandler[] valveHandlers = null;
	private static IWateringHandler pumpHandler = null;
	private static ArchiveData ad = null;
	private static MoistureCheck mc;
	private static final Logger logger = Logger.getLogger(Watering.class);
	private static Parameters parms = null;
	private static Date now;
	private static Date lastArchive;
	private static int dayOfTheWeek;
	private static SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");
	private static SimpleDateFormat longFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Usage: java -jar Watering.jar <config_file> [disable]");
			System.exit(1);
		}

		parms = Parameters.getInstance(args[0]);

		System.setProperty("DBHOST", parms.getDbHost());
		logger.debug("Got parms from configuration file. DBHOST is " + parms.getDbHost() +
					 " (" + System.getProperty("DBHOST") + ")");
		
		rtData = new RealTimeData(parms);
		rtData.setErrorCode(0);
		logger.debug("Data structures created");
		switch(parms.getRunMode())
		{
		case "a":
			rtData.setMode("auto");
			rtData.setErrorCode(rtData.getErrorCode() & 0b111111101111111111111111);
			break;
		case "m":
			rtData.setMode("manual");
			rtData.setErrorCode(rtData.getErrorCode() | Errors.STATUS_MANUAL);
			break;
		}
		logger.debug("System will be running in '" + rtData.getMode() + "' mode" );

		if((args.length >= 2) && (args[1].compareTo("disable") == 0))
		{
			rtData.setDisableFlag(true);
		}

		ad = new ArchiveData(rtData);
		sleep(500);	

		// Start user command interfaces
		AdminCommands ac;
		logger.debug("Starting Admin commands thread");
		ac = new AdminCommands(rtData);
		ac.start();
		sleep(500);

		JsonAdminCommands jsonAc;
		logger.debug("Starting JSON Admin commands thread");
		jsonAc = new JsonAdminCommands(rtData);
		jsonAc.start();
		sleep(500);
		
		// start handlers
		startHandlers();
		
		// initialize data for cycle automation
		rtData.evalFirstStart();

		logger.debug("First start time set to " + rtData.getNextStartTime());
		logger.debug("current day of the week " + dayOfWeekAsNumber(new Date()));
		logger.debug("valveHandlers is " + (valveHandlers == null ? "null" : "instanciated and its members are"));
		if (valveHandlers != null)
		{
			for(int i = 0; i < parms.getZones(); i++)
			{
				logger.debug("[" + i + "]: " + valveHandlers[i]);
			}
		}
		
		// start the effective job
		startDuties();

		sleep(3000);
		
		logger.debug("Waiting for thread shutdown");
		boolean done = false;
		while(!done)
		{
			sleep(3000);
			done = true;
			if ((sensorHandler != null) && sensorHandler.isInitialized())
			{
				logger.trace("Sensor handler still alive");
				done = false;
			}
			if ((pumpHandler != null) && pumpHandler.isInitialized())
			{
				logger.trace("Pump handler still alive");
				done = false;
			}
			if (valveHandlers != null) 
			{
				for(int i = 0; i < parms.getZones(); i++)
				{
					if ((valveHandlers[i] != null) && valveHandlers[i].isInitialized())
					{
						logger.trace("Valve " + i + " handler still alive");
						done = false;
					}
				}
			}
			if ((errorsHandler != null) && errorsHandler.isInitialized())
			{
				logger.trace("Error handler still alive");
				done = false;
			}
		}
		logger.debug("Threads ended. Exit program");
		System.exit(0);
	}
		
	private static void startHandlers() {
		if (parms.isUseMoistureSensor())
		{
			try 
			{
				logger.debug("Starting Sensor Handler thread");
				sensorHandler = SensorDataHandlerFactory.createHandler(rtData);
				sensorHandler.start();
			}
			catch (Exception e)
			{
				logger.error("Exception " + e.getMessage() + " creating SensorHandler thread");
			}
			sleep(500);
		}		

		valveHandlers = new IWateringHandler[parms.getZones()];
		for(int i = 0; i < parms.getZones(); i++)
		{
			try {
				logger.debug("Starting Valve " + i + " Handler thread");
				valveHandlers[i] = ValveHandlerFactory.createHandler(rtData, i, ad);
				valveHandlers[i].start();
				logger.debug("valveHandlers[" + i + "] is set to " + valveHandlers[i]);
				sleep(250);	
			} catch (Exception e) {
				logger.error("Exception " + e.getMessage() + " creating ValveHandler thread");
			}
		}
		sleep(500);

		if (parms.isEnablePump())
		{
			try {
				logger.debug("Starting Pump Handler thread");
				pumpHandler = PumpHandlerFactory.createHandler(rtData);
				pumpHandler.start();
				sleep(500);
			} catch (Exception e) {
				logger.error("Exception " + e.getMessage() + " creating PumpHandler thread");
			}
		}		
	}

	private static void startDuties()
	{
		mc = new MoistureCheck(rtData);
		mc.start();
		sleep(500);

		rtData.setInCycle(-1);
		logger.debug("Starting duties");
		while (!rtData.isShutDown()) 
		{
			checkAndStartThreads();
			isTimeToStart();
			sleep(1000);
		}
	}

	private static boolean isTimeToStart()
	{
		if (rtData.isRequiredChangeOnStartTime())
		{
			logger.warn("Requested to delay the start by " + rtData.getDelayByMinutes() + 
					" minutes from the original timestamp " );
			// A delay on the start time has been requested. Evaluate the new starting time
			rtData.setRequiredChangeOnStartTime(false);
			try
			{
				logger.warn("Original start time was set to " + rtData.getNextStartTime());
				Date startAtDate = 
						new Date(yyyyMMdd.parse(rtData.getNextStartTime()).getTime() + rtData.getDelayByMinutes() * 60000);
				logger.warn("New start time set to " + longFmt.format(startAtDate));
				rtData.setNextStartTime(longFmt.format(startAtDate));
			} 
			catch (ParseException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		now = new Date();
		if (lastArchive == null)
		{
			lastArchive = new Date(now.getTime() - 60000);
		}
		
		if ((rtData.getInCycle() < 0) && !rtData.isForceManual() && 
			(longFmt.format(now).compareTo(rtData.getNextStartTime()) < 0))
		{
			logger.trace("no reasons to evaluate a start" );
			return false;
		}
		
		if (rtData.isDisableFlag())
		{
			logger.trace("a request to disable watering has been received" );
			// request to disable watering raised
			if (rtData.getInCycle() >= 0)
			{
				logger.trace("watering is active on valve " + rtData.getInCycle() + ". Disable it");
				// we are in watering phase. stop watering and reset the cycle indicator
				// Archive the watering time for the current zone
				ad.archive(History.TYPE_WATERING_TIME, rtData.getInCycle(), rtData.getWateringTimeElapsed(rtData.getInCycle()));
				
				// Switch off the current zone valve
				rtData.setValveStatus(rtData.getInCycle(), false);
				logger.debug("Watering terminated due to disable flag");
			}
			rtData.evalNextStartTime(true);
			logger.debug("Reavaluated next start time to " + rtData.getNextStartTime());
			rtData.setInCycle(-1);
			rtData.setForceManual(false);
			rtData.setSkipCycleFlag(false);
			rtData.setSuspendFlag(false);
			return false;
		}
		
		if (rtData.isSkipCycleFlag())
		{
			// The current cycle should be skipped either by manual or automatic request.
			logger.debug("Skip request has been raised. No watering for this cycle");
			logger.debug("Current watering time set to " + rtData.getNextStartTime() + " (will be reset)");
			rtData.setLastStart(now);
			rtData.evalNextStartTime(true);
			logger.debug("Next watering time set to " + rtData.getNextStartTime());
			rtData.setSkipCycleFlag(false);
			rtData.setForceManual(false);
			rtData.setSuspendFlag(false);
            for(int zone = 0; zone < parms.getZones(); zone++)
            {
            	rtData.setValveStatus(zone, false);
            }
            rtData.setInCycle(-1);
			return false;
		}

		boolean newCycleStarting = false;
		if (rtData.getInCycle() == -1)
		{
			logger.trace("Conditions are set to start a new cycle");
			newCycleStarting = true;
			rtData.setLastWateringSession(now);
			// it's a new start requested
			// A start is effectively requested. Archive all moistures
			archiveAllMoistures(true, History.TYPE_MOISTURE_AT_START);

			rtData.setInCycle(0);
			rtData.setLastStart(now);
			// Find the first zone with a watering time > 0
			while((parms.getDuration(rtData, dayOfTheWeek) == 0) && (rtData.getInCycle() < parms.getZones()))
			{
				rtData.setInCycle(rtData.getInCycle() + 1);
			}
			logger.trace("The first area to water will be " + rtData.getInCycle());
		}

		if (rtData.getInCycle() >= parms.getZones())
		{
			// either no watering required or one zone made current to watering. 
			// so... either we're done or a new cycle starts
			logger.debug("No more watering required in this run");
			rtData.evalNextStartTime(true);
			logger.debug("Next start time set to " + rtData.getNextStartTime());
			rtData.setForceManual(false);
			rtData.setLastWateringSession(rtData.getNextStartTimeAsDate());
			rtData.setInCycle(-1);
			return false;
		}
		
		if(newCycleStarting)
		{	
			// It's a fresh start. We need to open-up the first valve in order to get it running.
			logger.debug("It seems time to start a new cycle or forced to do it manually");
			logger.debug("Start watering zone " + rtData.getInCycle() + " for " + parms.getDuration(rtData, dayOfTheWeek) + " sec");
			int expDurations = -1;
			int startIdx = rtData.getNextStartIdx();
			for(int i = 0; i < parms.getZones(); i++)
			{
				expDurations = parms.getDurations()[i][startIdx][dayOfTheWeek] * 60;
				rtData.setWateringTimeTarget(i, expDurations);
			}
			logger.debug("Watering zone " + rtData.getInCycle() + 
						 " for " + rtData.getWateringTimeTarget(rtData.getInCycle()) + " sec");
			newCycleStarting = false;
			for(int i = 0; i < parms.getZones(); i++)
			{
				rtData.setWateringTimeElapsed(i, 0);
			}
			rtData.setValveStatus(rtData.getInCycle(), true);
		}

		if (rtData.getWateringTimeElapsed(rtData.getInCycle()) >= rtData.getWateringTimeTarget(rtData.getInCycle()))
		{
			logger.trace("The area " + rtData.getInCycle() + " watering has been completed");
			rtData.setValveStatus(rtData.getInCycle(), false);
			rtData.setWateringTimeTarget(rtData.getInCycle(), 0);
			// being in cycle, get the next zone for which a duration > 0 is set 
			rtData.setInCycle(rtData.getInCycle() + 1);
			while((rtData.getInCycle() < parms.getZones()) && (parms.getDuration(rtData, dayOfTheWeek) == 0))
			{
				rtData.setInCycle(rtData.getInCycle() + 1);
			}
			logger.debug("the next area to water is " + rtData.getInCycle());
		}
		
		if ((rtData.getInCycle() < parms.getZones()) && !rtData.getValveStatus(rtData.getInCycle()))
		{
			logger.debug("opening the area valve");
			rtData.setValveStatus(rtData.getInCycle(), true);
		}
		
		return true;
	}
	
	private static void archiveAllMoistures(boolean force, final int recordType)
	{	
		// Check if it is time to archive the moisture read by sensors and do it
		if (((now.getTime() - lastArchive.getTime()) > parms.getSensorValueDumpInterval() * 1000) || force)
		{
			for(int i = 0; i < parms.getNumberOfSensors(); i++)
			{
				archiveMoisture(i, recordType);
			}
			lastArchive = now;
		}
	}

	private static void archiveMoisture(int sensordId, final int recordType)
	{	
		if (rtData.getMoisture(sensordId) != null)
		{
			ad.archive(recordType, sensordId, rtData.getMoisture(sensordId),
				   rtData.getSensorReadValue(sensordId), rtData.getSensorRangeFrom(sensordId), rtData.getSensorRangeTo(sensordId));
		}
	}
	
	private static void checkAndStartThreads()
	{
		logger.trace("Checking thread status");
		logger.trace("errorsHandler is null? " + (errorsHandler == null) +
					 " isAlive " + (errorsHandler == null ? "NA" : errorsHandler.isInitialized()));
		if ((errorsHandler == null) || !errorsHandler.isInitialized())
		{
			logger.debug("Restarting Error Handler thread");
			errorsHandler = null;
			try 
			{
				errorsHandler = ErrorsHandlerFactory.createErrorsHandler(rtData);
				errorsHandler.start();
			}
			catch(Exception e)
			{
				logger.error("Exception " + e.getMessage() + " creating ErrorHandler thread");
			}
		}

		if (parms.isUseMoistureSensor())
		{
			logger.trace("sensorHandler is null? " + (sensorHandler == null) +
					 " isAlive " + (sensorHandler == null ? "NA" : sensorHandler.isInitialized()));
			if ((sensorHandler == null) || !sensorHandler.isInitialized())
			{
				logger.debug("Restarting Sensor Handler thread");
				sensorHandler = null;
				try 
				{
					sensorHandler = SensorDataHandlerFactory.createHandler(rtData);
					sensorHandler.start();
				}
				catch (Exception e)
				{
					logger.error("Exception " + e.getMessage() + " creating SensorHandler thread");
				}
			}
		}
		
		if (parms.isEnablePump()) 
		{
			logger.trace("pumpHandler is null? " + (pumpHandler == null) +
					 " isAlive " + (pumpHandler == null ? "NA" : pumpHandler.isInitialized()));
			if ((pumpHandler == null) || !pumpHandler.isInitialized())
			{
				logger.debug("Restarting Pump Handler thread");
				pumpHandler = null;
				try 
				{
					pumpHandler = PumpHandlerFactory.createHandler(rtData);
					pumpHandler.start();
				}
				catch (Exception e)
				{
					logger.error("Exception " + e.getMessage() + " creating PumpHandler thread");
				}
			}
		}
		
		if (valveHandlers != null) 
		{
			for(int i = 0; i < parms.getZones(); i++)
			{
				logger.trace("valveHandlers[" + i + "] is null? " + (valveHandlers[i] == null) +
						 " isAlive " + (valveHandlers[i] == null ? "N/A" : valveHandlers[i].isInitialized()));

				if ((valveHandlers[i] == null) || !valveHandlers[i].isInitialized())
				{
					logger.debug("Restarting Valve Handler " + i + " thread");
					valveHandlers[i] = null;
					try 
					{
						valveHandlers[i] = ValveHandlerFactory.createHandler(rtData, i, ad);
						valveHandlers[i].start();
					}
					catch (Exception e)
					{
						logger.error("Exception " + e.getMessage() + " creating ValveHandler thread");
					}
				}
			}
		}
		if (parms.isEnableAutoSkip())
		{
			logger.trace("pumpHandler is null? " + (mc == null) +
					 " isAlive " + (mc == null ? "NA" : mc.isInitialized()));
			if ((mc == null) || ! mc.isInitialized())
			{
				logger.debug("Starting Moisture check thread");
				rtData.setRunMoistureCheck(true);
				mc = null;
				mc = new MoistureCheck(rtData);
				mc.start();
			}
		}
		else
		{
			if (rtData.isRunMoistureCheck())
			{
				logger.debug("Stopping Moisture check thread");
				rtData.setRunMoistureCheck(false);
			}
		}
	}

	private static void sleep(int millis)
	{
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			logger.error("Sleep interrupted: " + e.getMessage());
			Thread.currentThread().interrupt();
		}
	}

	public static int dayOfWeekAsNumber(Date now)
	{
		Calendar cal = Calendar.getInstance();
		cal.setTime(now);
		return cal.get(Calendar.DAY_OF_WEEK);
	}
}
