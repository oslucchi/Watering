package it.lsoft.watering;


import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.DBUtils.ArchiveData;
import it.lsoft.watering.DBUtils.History;
import it.lsoft.watering.Raspberry.ErrorsHandler;
import it.lsoft.watering.Raspberry.PumpHandler;
import it.lsoft.watering.Raspberry.RealTimeData;
import it.lsoft.watering.Raspberry.SensorDataHandler;
import it.lsoft.watering.Raspberry.ValveHandler;
import it.lsoft.watering.AdminCommands;


public class Watering 
{
	private static final int ONE_HOUR = 1000*60*60;
	private static final int FIFTEEN_MINUTES = 1000*60*15;
	
	private static RealTimeData rtData = null;
	private static ErrorsHandler eh = null;
	private static SensorDataHandler sh = null;
	private static ValveHandler[] vh = null;
	private static PumpHandler ph = null;
	private static ArchiveData ad = null;
	private static final Logger logger = Logger.getLogger(Watering.class);
	private static Parameters parms = null;
	private static Date now;
	private static Date lastArchive;
	private static int dayOfTheWeek;
	private static SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");
	private static SimpleDateFormat longFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	private static int inCycle = -1;		

	private static int dayOfWeekAsNumber(Date now)
	{
		Calendar c = Calendar.getInstance();
		c.setTime(now);
		switch(c.get(Calendar.DAY_OF_WEEK))
		{
		case Calendar.MONDAY:
			return 0;
		case Calendar.TUESDAY:
			return 1;
		case Calendar.WEDNESDAY:
			return 2;
		case Calendar.THURSDAY:
			return 3;
		case Calendar.FRIDAY:
			return 4;
		case Calendar.SATURDAY:
			return 5;
		case Calendar.SUNDAY:
			return 6;
		}
		return -1;
	}
	
	private static void sleep(int mills)
	{
		try {
			Thread.sleep(mills);
		} 
		catch (InterruptedException e1) {
			;
		}

	}
	/**
	 * @param args
	 * @throws IOException 
	 * @throws WateringException 
	 */
	public static void main(String[] args) 
	{

		parms = Parameters.getInstance(args[0]);

		System.setProperty("DBHOST", parms.getDbHost());
		logger.debug("Got parms from configuration file. DBHOST is " + parms.getDbHost() +
					 " (" + System.getProperty("DBHOST") + ")");
		
		rtData = new RealTimeData(parms);
		logger.debug("Data structures created");
		
		// Error Handler spawn
		rtData.setErrorCode(0);

		if((args.length >= 3) && (args[2].compareTo("disable") == 0))
		{
			rtData.setDisableFlag(true);
		}

		// Data archiver
		ad = new ArchiveData(rtData);

		try 
		{
			logger.debug("Starting Error Handler thread");
			eh = new ErrorsHandler(rtData);
			eh.start();
		}
		catch(Exception e)
		{
			logger.error("Exception " + e.getMessage() + " creating ErrorHandler thread");
		}
		
		sleep(500);	
		
		// ADC Handler spawn
		if (parms.isUseMoistureSensor())
		{
			try 
			{
				logger.debug("Starting Sensor Handler thread");
				sh = new SensorDataHandler(rtData);
				sh.start();
			}
			catch (IOException e)
			{
				logger.error("Exception " + e.getMessage() + " creating SensorHandler thread");
			}
			sleep(500);
		}		
		// Valve handler spawn
		vh = new ValveHandler[parms.getZones()];
		for(int i = 0; i < parms.getZones(); i++)
		{
			vh[i] = null;
			logger.debug("Starting Valve " + i + " Handler thread");
			vh[i] = new ValveHandler(rtData, i, ad);
			vh[i].start();
			sleep(250);	
		}
		sleep(500);

		if (parms.isEnablePump())
		{
			// Pumphandler spawn
			logger.debug("Starting Pump Handler thread");
			ph = new PumpHandler(rtData);
			ph.start();
			
			sleep(500);
		}
		
		AdminCommands ac;
		logger.debug("Starting Admin commands thread");
		ac = new AdminCommands(rtData);
		ac.start();
		sleep(500);
		
		rtData.setInCycle(inCycle);
		rtData.evalNextStartTime(false);

		logger.debug("First start time set to " + rtData.getNextStartTime());
		logger.debug("current day of the week " + dayOfWeekAsNumber(new Date()));
		
		startDuties(); // main watering cycle handler

		sleep(3000);
		logger.debug("Waiting for thread shutdown");
		boolean done = false;
		while(!done)
		{
			sleep(3000);
			done = true;
			if ((sh != null) && sh.isAlive())
			{
				logger.trace("Sensor handler still alive");
				done = false;
			}
			if ((ph != null) && ph.isAlive())
			{
				logger.trace("Pump handler still alive");
				done = false;
			}
			if (vh != null) 
			{
				for(int i = 0; i < parms.getZones(); i++)
				{
					if ((vh[i] != null) && vh[i].isAlive())
					{
						logger.trace("Valve " + i + " handler still alive");
						done = false;
					}
				}
			}
			if ((eh != null) && eh.isAlive())
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
		lastArchive = new Date();
		rtData.setLastWateringSession(rtData.getNextStartTimeAsDate());
		while(!rtData.isShutDown())
		{
			parms = rtData.getParms();
			
			checkAndStartThreads();
			
			now = new Date();
			dayOfTheWeek = dayOfWeekAsNumber(now);
			evalAndArchiveMoisture();
									
			inCycle = rtData.getInCycle();
			if (isTimeToStart())
			{
				rtData.setLastWateringSession(now);
				// A watering cycle is currently active
				if (rtData.getWateringTimeElapsed(inCycle) > parms.getDuration(rtData, dayOfTheWeek))
				{
					// The watering time is over. Move to the next area requiring to be watered
					
					// Archive the moisture level so to have an evaluation of how much the watering
					// cycle impacts on the ground moisture for future tuning.
					for(int i = 0; i < parms.getNumberOfSensors(); i++)
					{
						if (rtData.getMoisture(i) != null)
						{
							ad.archive(History.TYPE_MOISTURE, inCycle, rtData.getMoisture(i));
						}
					}
					
					// Archive the watering time for the current zone
					ad.archive(History.TYPE_WATERING_TIME, inCycle, rtData.getWateringTimeElapsed(inCycle));
					
					// Switch off the current zone valve
					rtData.setValveStatus(inCycle, false);
					logger.debug("Watering time for zone " + inCycle + " reached. Moving to the next zone");
					
					// Move to the next zone skipping those whose watering time is zero
					inCycle++;
					rtData.setInCycle(inCycle);
					while((inCycle < parms.getZones()) && (parms.getDuration(rtData, dayOfTheWeek) == 0))
					{
						logger.debug("Skipping zone " + inCycle + " having watering time set to 0");
						inCycle++;
						rtData.setInCycle(inCycle);
					}
					if (inCycle < parms.getZones())
					{
						// A new area requiring to be watered has to be activated
						sleep(2000);
						logger.debug("Start watering zone " + inCycle + " for " + parms.getDuration(rtData, dayOfTheWeek) + " sec");
						rtData.setValveStatus(inCycle, true);
						logger.debug("Watering zone " + inCycle + 
									 " for " + parms.getDurations()[inCycle][rtData.getNextStartIdx()][dayOfTheWeek] * 60 + " sec");
					}
				}
			}
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
				logger.debug("Watering time for zone " + inCycle + " terminated due to disable flag");
			}
			rtData.evalNextStartTime(true);
			logger.debug("Reavaluated next start time to " + rtData.getNextStartTime());
			inCycle = -1;
			rtData.setInCycle(inCycle);
			rtData.setForceManual(false);
			rtData.setSkipFlag(false);
			rtData.setSuspendFlag(false);
			return false;
		}
		
		if (rtData.isSkipFlag())
		{
			// The current cycle should be skipped either by manual or automatic request.
			logger.debug("Skip request has been raised. No watering for this cycle");
			logger.debug("Current watering time set to " + rtData.getNextStartTime() + " (will be reset)");
			rtData.evalNextStartTime(true);
			logger.debug("Next watering time set to " + rtData.getNextStartTime());
			rtData.setSkipFlag(false);
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
			for(int i = 0; i < parms.getNumberOfSensors(); i++)
			{
				if (rtData.getMoisture(i) != null)
				{
					ad.archive(History.TYPE_MOISTURE, inCycle, rtData.getMoisture(i));
				}
			}
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
			inCycle = -1;
			rtData.setInCycle(-1);
			logger.debug("No watering required in this run");
			rtData.evalNextStartTime(false);
			logger.debug("Next start time set to " + rtData.getNextStartTime());
			rtData.setForceManual(false);
			return false;
		}
		
		if(newCycleStarting)
		{
			// It's a fresh start. We need to open-up the first valve in order to get it running.
			logger.debug("It seems time to start a new cycle or forced to do it manually");
			logger.debug("Start watering zone " + inCycle + " for " + parms.getDuration(rtData, dayOfTheWeek) + " sec");
			rtData.setValveStatus(inCycle, true);
			logger.debug("Watering zone " + inCycle + 
						 " for " + parms.getDurations()[inCycle][rtData.getNextStartIdx()][dayOfTheWeek] * 60 + " sec");
		}
		return true;
	}
	
	private static void evalAndArchiveMoisture()
	{	
		boolean checkSkipConditions = 	(
											// Check only if we are 1 hour before watering and no skipFlag has been raised yet
											(now.getTime() > rtData.getNextStartTimeAsDate().getTime() - ONE_HOUR) && 
											(inCycle < 0) &&
											!rtData.isSkipFlag()
										);
		boolean checkWateringEffectiveness = ((now.getTime() > rtData.getLastWateringSession().getTime() + FIFTEEN_MINUTES));
		
		/*
		 * Check if it is time to archive the moisture read by sensors and do it
		 */
		if ((now.getTime() - lastArchive.getTime()) > parms.getSensorValueDumpInterval() * 1000)
		{
			logger.debug("checkSkipConditions: " + checkSkipConditions);
			logger.debug("checkWateringEffectiveness: " + checkWateringEffectiveness + "(" + rtData.getLastWateringSession() + ")");

			for(int i = 0; i < parms.getNumberOfSensors(); i++)
			{
				if (rtData.getMoisture(i) != null)
				{
					ad.archive(History.TYPE_MOISTURE, i, rtData.getMoisture(i),
						   rtData.getSensorReadValue(i), rtData.getSensorRangeFrom(i), rtData.getSensorRangeTo(i));
					logger.trace("skiptThreshold for the sensor: " + parms.getSkipTreshold()[i]);
					if (checkSkipConditions && (rtData.getMoisture(i) > parms.getSkipTreshold()[i]))
					{
						logger.debug("Moisture on sensor " + i + " is bigger than the related treshold (" +
								rtData.getMoisture(i) + " - " + parms.getSkipTreshold()[i] + "). Setting the skipFlag to " + 
								rtData.getParms().isEnableAutoSkip());
						rtData.setSkipFlag(rtData.getParms().isEnableAutoSkip());
					}
					logger.trace("expectedMoisture for the sensor: " + parms.getExpectedMoistureAfterWatering()[i]);
					if (checkWateringEffectiveness && (rtData.getMoisture(i) < parms.getExpectedMoistureAfterWatering()[i]))
					{
						logger.error("Moisture on sensor " + i + " did not reach the expected level  during last watering session(" +
								rtData.getMoisture(i) + " - " + parms.getExpectedMoistureAfterWatering()[i] + ").");
						logger.error("Reset the skip and autoSkip flag");
						rtData.setSkipFlag(false);
						rtData.getParms().setEnableAutoSkip(false);
					}
				}
			}
			if (checkWateringEffectiveness)
			{
				rtData.setLastWateringSession(rtData.getNextStartTimeAsDate());
			}
			lastArchive = now;
		}
	}
	
	private static void checkAndStartThreads()
	{
		if ((eh == null) || !eh.isAlive())
		{
			logger.debug("Restarting Errors Handler thread");
			eh = null;
			try 
			{
				eh = new ErrorsHandler(rtData);
				eh.start();
			}
			catch(Exception e)
			{
				logger.error("Exception " + e.getMessage() + " creating ErrorHandler thread");
			}
		}

		if (parms.isUseMoistureSensor() && ((sh == null) || !sh.isAlive()))
		{
			logger.debug("Restarting Sensor Handler thread");
			sh = null;
			try 
			{
				sh = new SensorDataHandler(rtData);
				sh.start();
			}
			catch (IOException e)
			{
				logger.error("Exception " + e.getMessage() + " creating SensorHandler thread");
			}
		}
		
		if (parms.isEnablePump() && ((ph == null) || !ph.isAlive()))
		{
			logger.debug("Restarting Pump Handler thread");
			ph = new PumpHandler(rtData);
			ph.start();
		}

		if (vh != null) 
		{
			for(int i = 0; i < parms.getZones(); i++)
			{
				if ((vh[i] == null) || !vh[i].isAlive())
				{
					logger.debug("Restarting Valve Handler " + i + " thread");
					vh[i] = null;
					vh[i] = new ValveHandler(rtData, i, ad);
					logger.debug("Starting Valve " + i + " Handler thread");
					vh[i].start();
				}
			}
		}
	}
}
