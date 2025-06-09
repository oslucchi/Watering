package it.lsoft.watering;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Errors;
import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.DBUtils.ArchiveData;
import it.lsoft.watering.DBUtils.History;
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
	private static int inCycle = -1;

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

		if((args.length >= 2) && (args[1].compareTo("disable") == 0))
		{
			rtData.setDisableFlag(true);
		}

		ad = new ArchiveData(rtData);

		try 
		{
			logger.debug("Starting Error Handler thread");
			initializeHandlers();
		}
		catch(Exception e)
		{
			logger.error("Exception " + e.getMessage() + " creating ErrorHandler thread");
		}
		
		sleep(500);	
		
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
		
		rtData.setInCycle(inCycle);
		rtData.evalFirstStart();

		logger.debug("First start time set to " + rtData.getNextStartTime());
		logger.debug("current day of the week " + dayOfWeekAsNumber(new Date()));
		
		startDuties();

		sleep(3000);
		
		logger.debug("Waiting for thread shutdown");
		boolean done = false;
		while(!done)
		{
			sleep(3000);
			done = true;
			if ((sensorHandler != null) && sensorHandler.isAlive())
			{
				logger.trace("Sensor handler still alive");
				done = false;
			}
			if ((pumpHandler != null) && pumpHandler.isAlive())
			{
				logger.trace("Pump handler still alive");
				done = false;
			}
			if (valveHandlers != null) 
			{
				for(int i = 0; i < parms.getZones(); i++)
				{
					if ((valveHandlers[i] != null) && valveHandlers[i].isAlive())
					{
						logger.trace("Valve " + i + " handler still alive");
						done = false;
					}
				}
			}
			if ((errorsHandler != null) && errorsHandler.isAlive())
			{
				logger.trace("Error handler still alive");
				done = false;
			}
		}
		logger.debug("Threads ended. Exit program");
		System.exit(0);
	}
		
	private static void startDuties()
	{
		mc = new MoistureCheck(rtData);
		mc.start();
		sleep(500);

		logger.debug("Starting duties");
		while (!rtData.isShutDown()) {
			checkAndStartThreads();
			sleep(1000);
		}
	}
	
	private static boolean isTimeToStart()
	{
		if (rtData.isRequiredChangeOnStartTime())
		{
			// A delay on the start time has been requested. Evaluate the new starting time
			rtData.setRequiredChangeOnStartTime(false);
			logger.warn("Requested to delay the start by " + rtData.getDelayByMinutes() + 
						" minutes from the original timestamp " );
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
		
		if (! ((inCycle >= 0) || (longFmt.format(now).compareTo(rtData.getNextStartTime()) == 0) || rtData.isForceManual()))
		{
			return false;
		}
		
		if (rtData.isDisableFlag())
		{
			// request to disable watering raised
			if (inCycle >= 0)
			{
				// we are in watering phase. stop watering and reset the cycle indicator
				// Archive the watering time for the current zone
				ad.archive(History.TYPE_WATERING_TIME, inCycle, rtData.getWateringTimeElapsed(inCycle));
				
				// Switch off the current zone valve
				rtData.setValveStatus(inCycle, false);
				logger.debug("Watering terminated due to disable flag");
			}
			rtData.evalNextStartTime(true);
			logger.debug("Reavaluated next start time to " + rtData.getNextStartTime());
			inCycle = -1;
			rtData.setInCycle(inCycle);
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
			return false;
		}

		boolean newCycleStarting = false;
		if (inCycle == -1)
		{
			newCycleStarting = true;
			// it's a new start requested
			// A start is effectively requested. Archive all moistures
			archiveAllMoistures(true, History.TYPE_MOISTURE_AT_START);

			inCycle = 0;
			rtData.setInCycle(0);
			rtData.setLastStart(now);
			// Find the first zone with a watering time > 0
			while((parms.getDuration(rtData, dayOfTheWeek) == 0) && (inCycle < parms.getZones()))
			{
				inCycle++;
				rtData.setInCycle(inCycle);
			}
		}

		if (inCycle >= parms.getZones())
		{
			// either no watering required or one zone made current to watering. 
			// so... either we're done or a new cycle starts
			logger.debug("No more watering required in this run");
			rtData.evalNextStartTime(true);
			logger.debug("Next start time set to " + rtData.getNextStartTime());
			rtData.setForceManual(false);
			rtData.setLastWateringSession(rtData.getNextStartTimeAsDate());
			inCycle = -1;
			rtData.setInCycle(-1);
			return false;
		}
		
		if(newCycleStarting)
		{
			if ((rtData.getMode().compareTo("manual") == 0) && !rtData.isForceManual())
			{
				// mode is set to manual but no force manual is requested.
				// stay there
				;
			}
			else
			{
				rtData.setLastWateringSession(now);
				
				// It's a fresh start. We need to open-up the first valve in order to get it running.
				logger.debug("It seems time to start a new cycle or forced to do it manually");
				logger.debug("Start watering zone " + inCycle + " for " + parms.getDuration(rtData, dayOfTheWeek) + " sec");
				rtData.setValveStatus(inCycle, true);
				logger.debug("Watering zone " + inCycle + 
							 " for " + parms.getDurations()[inCycle][rtData.getNextStartIdx()][dayOfTheWeek] * 60 + " sec");
			}
		}
		return true;
	}
	
	private static void archiveAllMoistures(boolean force, final int recordType)
	{	
		/*
		 * Check if it is time to archive the moisture read by sensors and do it
		 */
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
		if ((errorsHandler == null) || !errorsHandler.isAlive())
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

		if (parms.isUseMoistureSensor() && ((sensorHandler == null) || !sensorHandler.isAlive()))
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
		
		if (parms.isEnablePump() && ((pumpHandler == null) || !pumpHandler.isAlive()))
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

		if (valveHandlers != null) 
		{
			for(int i = 0; i < parms.getZones(); i++)
			{
				if ((valveHandlers[i] == null) || !valveHandlers[i].isAlive())
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
			if ((mc == null) || ! mc.isAlive())
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

	private static void initializeHandlers() throws Exception {
		errorsHandler = ErrorsHandlerFactory.createErrorsHandler(rtData);
		sensorHandler = SensorDataHandlerFactory.createHandler(rtData);
		pumpHandler = PumpHandlerFactory.createHandler(rtData);

		valveHandlers = new IWateringHandler[rtData.getParms().getZones()];
		for (int i = 0; i < rtData.getParms().getZones(); i++) {
			valveHandlers[i] = ValveHandlerFactory.createHandler(rtData, i, ad);
		}

		// Start all handlers
		errorsHandler.start();
		sensorHandler.start();
		pumpHandler.start();
		for (IWateringHandler handler : valveHandlers) {
			handler.start();
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
