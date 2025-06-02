package it.lsoft.watering;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.Commons.Utility;
import it.lsoft.watering.Raspberry.RealTimeData;
import it.lsoft.watering.Raspberry.IWateringHandler;

public class MoistureCheck extends Thread implements IWateringHandler
{
	private class MoistureCheckEntry {
		private boolean checked;
		private Date timeToCheck;
		public MoistureCheckEntry()
		{
			checked = false;
		}
	}
	
	private RealTimeData rtData;
	private Date now;
	private MoistureCheckEntry[] checkList = null;
	private Parameters parms = null;

	private static final Logger logger = Logger.getLogger(MoistureCheck.class);

	public MoistureCheck(RealTimeData rtData)
	{
		this.rtData = rtData;
		logger.info("Initialized Moisture Check");
	}
	
	@Override
	public void run() 
	{
		logger.debug("Moisture Check thread started");
		SimpleDateFormat longFmt = rtData.getLongFmt();
		parms = rtData.getParms();
		
		while (!rtData.isShutDown())
		{
			try 
			{
				Thread.sleep(1000);
			} 
			catch (InterruptedException e) 
			{
				logger.error("Error in moisture check: " + e.getMessage());
				Thread.currentThread().interrupt();
				break;
			}
			if (!parms.isEnableAutoSkip())
			{
				continue;
			}
			
			now = new Date();
			// Initiate the watering effectiveness check only if: the set-time from watering is over, 
			// it wasn't initiated already (checkList = null) and it wasn't requested a skip for this cycle
			if ((longFmt.format(now).compareTo(rtData.getNextStartTime()) == 0) && (checkList == null))
			{
				logger.info("watering effectiveness time is arrived. Set the checktime array accordingly");
				if  (rtData.isSkipCycleFlag() || rtData.isDisableFlag() || rtData.isSuspendFlag())
				{
					logger.info("...but unfortunately the watering request is somehow cancelled (Skip-Dis-Susp: " +
								rtData.isSkipCycleFlag() + " - " + rtData.isDisableFlag() + " - " + rtData.isSuspendFlag() + ")." );
					logger.info("Going back to rest");
				}
				else
				{
					checkList = new MoistureCheckEntry[parms.getZones()];
					checkList[0] = new MoistureCheckEntry();
					checkList[0].timeToCheck = new Date(
											now.getTime() + 
											parms.getZoneDuration(0, rtData, Watering.dayOfWeekAsNumber(now)) * 1000 +
											parms.getMinutesToEvalWateringEffectivess() * 1000 * 60
										);
					for(int i = 1; i < parms.getZones(); i++)
					{
						checkList[i] = new MoistureCheckEntry();
						checkList[i].timeToCheck = new Date(
											checkList[i - 1].timeToCheck.getTime() + 
											parms.getZoneDuration(i, rtData, Watering.dayOfWeekAsNumber(now)) * 1000
										);	   
					}
				}
			}
			evalMoistureEffectiveness();

			evalSkipNextCycle();
		}
		logger.debug("Moisture check thread exiting");
	}
	
	private void evalMoistureEffectiveness()
	{		
		if (checkList != null)
		{
			for(int i = parms.getZones() - 1; i >= 0; i--)
			{
				if (! checkList[i].checked) 
				{
					if (now.getTime() > checkList[i].timeToCheck.getTime())
					{
						logger.debug("Zone " + i + ": ");
						logger.debug("\ttime to check was '" + rtData.getLongFmt().format(checkList[i].timeToCheck) + "'");
						logger.debug("\tchecked flag was '" + checkList[i].checked + "'");
						logger.debug("marked as moisture checked");
						checkList[i].checked = true;
						
						int sensorId = parms.getSensorIdPerArea()[i];
						if (sensorId != -1) 
						{
							logger.debug("Check watering effectiveness on the area " + sensorId);
							if ((rtData.getMoisture(sensorId) != null) &&
								(rtData.getMoisture(sensorId) < parms.getExpectedMoistureAfterWatering()[sensorId]))
							{
								String mailBody = "Moisture on sensor " + sensorId + 
												  " did not reach the expected level  during last watering session(" +
												  rtData.getMoisture(sensorId) + " - " + 
												  parms.getExpectedMoistureAfterWatering()[sensorId] + ").";
								logger.error(mailBody);
								logger.error("Reset the skip and autoSkip flag");
								rtData.setSkipCycleFlag(false);
								parms.setEnableAutoSkip(false);
								Utility.sendAlertByMail(parms, mailBody);
							}
							else
							{
								logger.error("Moisture on sensor " + sensorId + " is at the expected level (" +
										rtData.getMoisture(sensorId) + " - " + 
										parms.getExpectedMoistureAfterWatering()[sensorId] + ").");
							}
						}
					}					
				}
			}
			boolean done = true;
			for(int i = 0; i < parms.getZones(); i++)
			{
				if (! checkList[i].checked)
				{
					done = false;
				}
			}
			if (done)
			{
				checkList = null;
				logger.error("Watering effectiveness check completed");
			}
		}
	}

	private void evalSkipNextCycle()
	{	
		// Check only if we are before the next cycle (so not in a watering cycle) and no skipFlag has been raised yet
		if ((now.getTime() < rtData.getNextStartTimeAsDate().getTime() - parms.getMinutesToSkipFlagCheck() * 60000) || 
			(rtData.getInCycle() >= 0) ||
			rtData.isSkipCycleFlag())
		{
			return;
		}
		
		/*
		 * Check if it is time to archive the moisture read by sensors and do it
		 */
		for(int i = 0; i < parms.getNumberOfSensors(); i++)
		{
			if (rtData.getMoisture(i) != null)
			{
				if (rtData.getMoisture(i) > parms.getSkipTreshold()[i])
				{
					String mailBody = "Moisture on sensor " + i + " is bigger than the related treshold (" +
									  rtData.getMoisture(i) + " - " + parms.getSkipTreshold()[i] + "). Setting the skipFlag to " + 
									  rtData.getParms().isEnableAutoSkip();
					logger.debug(mailBody);
					rtData.setSkipCycleFlag(true);
					Utility.sendAlertByMail(parms, mailBody);
				}
			}
		}
	}
}
