package it.lsoft.watering.Raspberry;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Parameters;

public class RealTimeData 
{
	private long errorCode = 0;
	private Double[] moisture;
	private Double[] sensorReadValue;
	private double[] sensorRangeFrom;
	private double[] sensorRangeTo;
	private boolean[] valveStatus;
	private int[] wateringTimeElapsed;
	private boolean shutDown = false;
	private boolean runMoistureCheck = false;
	private boolean forceManual = false;
	private int	delayByMinutes = 0;
	private boolean requiredChangeOnStartTime = false;
	private Parameters parms;
	private String[] startAt;
	private String nextStartTime;
	private int inCycle = -1;
	private boolean skipCycleFlag = false;
	private boolean skipZoneFlag  = false;
	private boolean suspendFlag = false;
	private boolean disableFlag = false;
	private Date lastWateringSession;
	private String mode = "auto";

	private Date now;
	private Date lastStart;
	private SimpleDateFormat yyyyMMdd = new SimpleDateFormat("yyyy-MM-dd");
	private SimpleDateFormat longFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	private int nextStart = 0;
	

	static Logger logger = Logger.getLogger(RealTimeData.class);
	public void evalFirstStart()
	{
		startAt = parms.getSchedule();
		SimpleDateFormat hhmm = new SimpleDateFormat("HH:mm");
		int i = 0;
		now = new Date();
		for( ; i < startAt.length && ((hhmm.format(now).compareTo(startAt[i]) > 0) || !parms.getActiveSchedules()[i]); i++)
			;
		if (i < startAt.length)
		{
			nextStart = i;
			nextStartTime = yyyyMMdd.format(now) + " " + startAt[i];
		}
		else
		{
			nextStart = 0;
			while(!parms.getActiveSchedules()[nextStart])
				nextStart++;
			nextStartTime = yyyyMMdd.format(now.getTime() + 60000 * 60 * 24) + " " + startAt[nextStart];
		}
		logger.debug("First start time set to " + getNextStartTime());
	}

	public RealTimeData(Parameters parms)
	{
		this.parms = parms;
		this.moisture = new Double[parms.getNumberOfSensors()];
		for(int i = 0; i < parms.getNumberOfSensors(); i++)
		{
			moisture[i] = null;
		}
		this.sensorReadValue = new Double[parms.getNumberOfSensors()];
		this.sensorRangeFrom = new double[parms.getNumberOfSensors()];
		this.sensorRangeTo = new double[parms.getNumberOfSensors()];
		this.wateringTimeElapsed = new int[parms.getZones()];
		this.valveStatus = new boolean[parms.getZones()];
		for(int i = 0; i < parms.getZones(); i++)
		{
			valveStatus[i] = false;
		}
		evalFirstStart();
	}
	
	public void evalNextStartTime(boolean skipCurrent)
	{
		now = new Date();
		String startAt = "";
		if (((longFmt.format(now).compareTo(getNextStartTime()) >= 0) && (inCycle >= 0)) || skipCurrent)
		{
			if (forceManual)
			{
				nextStart = -1;
				logger.trace("manual start was forced, rescan the whole schedule line to find next");
			}
			while(++nextStart < getStartAt().length)
			{
				if (!parms.getActiveSchedules()[nextStart])
					continue;
				startAt = yyyyMMdd.format((lastStart == null ? now : lastStart)) + " " + getStartAt()[nextStart];
				logger.trace("start schedule " + nextStart + " is " + startAt + ". Now is " + longFmt.format(now));
				if (longFmt.format(now).compareTo(startAt) < 0)
				{
					logger.trace("Next start found at index " + nextStart);
					break;
				}
			}
			if (nextStart == getStartAt().length)
			{
				nextStart = 0;
				while(!parms.getActiveSchedules()[nextStart])
					nextStart++;
				logger.trace("Gone over last for day, set to first next day");
				// next start is for tomorrow. Add a day to the last start
				now = new Date(lastStart.getTime() + 60000 * 60 * 24);
				startAt = yyyyMMdd.format(now) + " " + getStartAt()[nextStart];
			}
			setNextStartTime(startAt);
			logger.trace("Next start time set to " + getNextStartTime());
		}

	}

	public void skipStartTime()
	{
	}

	public void persistData(int type, int pos, double value) throws IOException
	{
	}
		
	public Double[] getMoisture() 
	{
		return moisture;
	}
	
	public Double getMoisture(int i)
	{
		if ((parms.getNumberOfSensors() > 0) && (i < parms.getNumberOfSensors()))
			return moisture[i];
		else
			return(null);
	}
	
	public void setMoisture(int pos, Double moisture) 
	{
		this.moisture[pos] = moisture;
	}
	
	public Double getSensorReadValue(int i)
	{
		if (parms.getNumberOfSensors() > 0)
			return sensorReadValue[i];
		else
			return(null);
	}
	
	public void setSensorReadValue(int pos, Double value) 
	{
		this.sensorReadValue[pos] = value;
	}
	
	public double getSensorRangeFrom(int i)
	{
		if (parms.getNumberOfSensors() > 0)
			return sensorRangeFrom[i];
		else
			return(0);
	}
	
	public void setSensorRangeFrom(int pos, double value) 
	{
		this.sensorRangeFrom[pos] = value;
	}
	
	public double getSensorRangeTo(int i)
	{
		if (parms.getNumberOfSensors() > 0)
			return sensorRangeTo[i];
		else
			return(0);
	}
	
	public void setSensorRangeTo(int pos, double value) 
	{
		this.sensorRangeTo[pos] = value;
	}
	
	public int[] getWateringTimeElapsed() 
	{
		return wateringTimeElapsed;
	}
	
	public int getWateringTimeElapsed(int pos) 
	{
		return wateringTimeElapsed[pos];
	}
	
	public void setWateringTimeElapsed(int pos, int wateringTimeElapsed) 
	{
		this.wateringTimeElapsed[pos] = wateringTimeElapsed;
	}

	public boolean[] getValveStatus() 
	{
		return valveStatus;
	}

	public boolean getValveStatus(int pos) 
	{
		return valveStatus[pos];
	}

	public void setValveStatus(int pos, boolean valveStatus) 
	{
		this.valveStatus[pos] = valveStatus;
	}

	public long getErrorCode() 
	{
		return errorCode;
	}

	public synchronized void setErrorCode(long errorCode) 
	{
		this.errorCode = errorCode;
	}

	public boolean isShutDown() {
		return shutDown;
	}

	public void setShutDown(boolean shutDown) {
		this.shutDown = shutDown;
	}

	public boolean isForceManual() {
		return forceManual;
	}

	public void setForceManual(boolean forceManual) {
		this.forceManual = forceManual;
	}

	public int getDelayByMinutes() {
		return delayByMinutes;
	}

	public void setDelayByMinutes(int delayByMinutes) {
		this.delayByMinutes = delayByMinutes;
	}

	public boolean isRequiredChangeOnStartTime() {
		return requiredChangeOnStartTime;
	}

	public void setRequiredChangeOnStartTime(boolean requiredChangeOnStartTime) {
		this.requiredChangeOnStartTime = requiredChangeOnStartTime;
	}

	public Parameters getParms() {
		return parms;
	}

	public void setParms(Parameters parms)
	{
		this.parms = parms;
		startAt = parms.getSchedule();
		int i = 0;
		while((i < startAt.length) && !parms.getActiveSchedules()[i])
		{
			i++;
		}
		if (i >= startAt.length)
			i = 0;
		nextStartTime = startAt[i];
	}

	public String getNextStartTime() {
		return nextStartTime;
	}
	
	public Date getNextStartTimeAsDate() {
		try 
		{
			return longFmt.parse(nextStartTime);
		}
		catch (ParseException e)
		{
			return new Date(0);
		}
	}
	
	public void setNextStartTime(String nextStartTime) {
		this.nextStartTime = nextStartTime;
	}
	
	public String[] getStartAt() {
		return startAt;
	}

	public int getInCycle() {
		return inCycle;
	}

	public void setInCycle(int inCycle) {
		this.inCycle = inCycle;
	}
	
	public int getNextStartIdx()
	{
		return nextStart;
	}

	public boolean isSkipCycleFlag() {
		return skipCycleFlag;
	}

	public void setSkipCycleFlag(boolean skipFlag) {
		this.skipCycleFlag = skipFlag;
	}

	public boolean isSuspendFlag() {
		return suspendFlag;
	}

	public void setSuspendFlag(boolean suspendFlag) {
		this.suspendFlag = suspendFlag;
	}

	public boolean isDisableFlag() {
		return disableFlag;
	}

	public void setDisableFlag(boolean disableFlag) {
		this.disableFlag = disableFlag;
		if (!disableFlag)
		{
			evalNextStartTime(false);
		}
	}
	
	public void setScheduleIndex(int schedule)
	{
		nextStart = schedule;
	}

	public Date getLastStart() {
		return lastStart;
	}

	public void setLastStart(Date lastStart) {
		this.lastStart = lastStart;
	}

	public Date getLastWateringSession() {
		return lastWateringSession;
	}

	public void setLastWateringSession(Date lastWateringSession) {
		if (mode.compareTo("auto") == 0)
		{
			this.lastWateringSession = lastWateringSession;
		}
	}

	public SimpleDateFormat getYyyyMMdd() {
		return yyyyMMdd;
	}

	public SimpleDateFormat getLongFmt() {
		return longFmt;
	}

	public boolean isRunMoistureCheck() {
		return runMoistureCheck;
	}

	public void setRunMoistureCheck(boolean runMoistureCheck) {
		this.runMoistureCheck = runMoistureCheck;
	}
	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
		if (mode.compareTo("auto") == 0)
		{
			evalNextStartTime(false);
		}
	}

	public boolean isSkipZoneFlag() {
		return skipZoneFlag;
	}

	public void setSkipZoneFlag(boolean skipZoneFlag) {
		this.skipZoneFlag = skipZoneFlag;
	}
}