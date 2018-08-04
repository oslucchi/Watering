package it.lsoft.watering.DBUtils;

import it.lsoft.watering.Raspberry.RealTimeData;
import it.lsoft.watering.Commons.Errors;
import it.lsoft.watering.DBUtils.DBConnection;
import it.lsoft.watering.Exceptions.WateringException;

import java.util.Date;

import org.apache.log4j.Logger;

public class ArchiveData extends Thread implements AutoCloseable
{
	private RealTimeData rtData;
	static Logger logger = Logger.getLogger(ArchiveData.class);
	private DBConnection conn = null;

	public ArchiveData(RealTimeData appData)
	{
		this.rtData = appData;
		try
		{
			conn = DBConnection.getInstance();
		}
		catch(Exception e)
		{
			logger.warn("Exception " + e.getMessage() + " connecting to DB");
		}
		logger.debug("Connection established");
	}

	public void close() throws Exception 
	{
		logger.debug("Closing connection");
		conn.finalize();
	}
	
	public void archive(int type, int unityId, double value)
	{
		archive(type, unityId, value, 0, 0, 0);
	}	

	public void archive(int type, int unityId, double value, double readValue, double rangeFrom, double rangeTo)
	{
		if (!rtData.getParms().isEnableDbArchive())
			return;
		try
		{
			History hist = new History();
			hist.setLogStatement(true);
			hist.setType(type);
			hist.setUnityId(unityId);
			hist.setValue(value);
			hist.setReadValue(readValue);
			hist.setRangeFrom(rangeFrom);
			hist.setRangeTo(rangeTo);
			hist.setTimestamp(new Date());
			hist.insert(conn, "idHistory", hist);
			rtData.setErrorCode(rtData.getErrorCode() & ~Errors.DB_CONNECTION_ERROR);
		}
		catch (Exception e) 
		{
			rtData.setErrorCode(rtData.getErrorCode() | Errors.DB_CONNECTION_ERROR);
			if (e.getClass().getName().equals("WateringException"))
			{
				logger.debug("Got error on insert " + 
							  ((WateringException) e).getErrorCode() + " - " + 
							  ((WateringException) e).getErrorDescription(), e);
			}
			else
			{
				logger.debug("Got error on insert " + e.getMessage(), e);
			}
		}
	}	
}
