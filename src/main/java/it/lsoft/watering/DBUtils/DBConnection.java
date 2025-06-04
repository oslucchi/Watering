package it.lsoft.watering.DBUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

import it.lsoft.watering.Exceptions.WateringException;

public class DBConnection 
{
	final Logger log = Logger.getLogger(this.getClass());
    protected ResultSet rs = null;
    protected ResultSetMetaData rsm = null;
    protected Statement st = null;
	protected Connection conn = null;
	private static DBConnection singletonInstance = null;
	
	protected DBConnection()
	{
		return;
	}

	public void getConnection() throws WateringException  
	{		
        // Skip actual connection in test mode
        if (isTestMode()) {
            log.debug("Test mode: Skipping actual database connection");
            return;
        }

    	String retVal = null;
 		Exception e1 = null;
		try
		{
			log.trace("DBHOST from properties '" + System.getProperty("DBHOST") + "'");
			String mysqlHostURL = "jdbc:mysql://" + System.getProperty("DBHOST") + "/Watering";
			log.trace("trying to connect to '" + mysqlHostURL + "'");
			conn = DriverManager.getConnection(mysqlHostURL, "Watering", "Watering");;
			st = conn.createStatement();
		}
		catch (SQLException e) 
		{
			retVal = "Error on database connection (" + e.getMessage() + ")";
			log.error(retVal, e);
			e1 = e;
		}
		if (retVal != null)
		{
			try 
			{
				finalize();
			} 
			catch (Throwable e) 
			{
				// No action required
				;
			}
			throw new WateringException(e1);
		}
	}
    
	protected void finalize() 
	{
        if (isTestMode()) {
            log.debug("Test mode: Skipping database cleanup");
            return;
        }

		// log.debug("Closing resources");
		try 
		{
			if (rs != null)
			{
				rs.close();
			}
			if (st != null)
			{
				st.close();
			}
			if(conn != null)
			{
				conn.close();
				conn = null;
			}
		}
		catch(Exception e)
		{
			;
		}
	}
	
	public void executeQuery(String sql, boolean logStatement) throws Exception
	{
        if (isTestMode()) {
            if (logStatement) {
                log.debug("Test mode: Skipping SQL execution: " + sql);
            }
            return;
        }

		if (logStatement) 
		{
			log.debug("executing query '"  + sql + "'");
		}

		StringTokenizer stok = new StringTokenizer(sql);
		String queryType = "";
		if (stok != null)
		{
			queryType = stok.nextToken().toUpperCase();
		}
		try
		{
			if ((queryType.compareTo("INSERT") == 0) ||
				(queryType.compareTo("DELETE") == 0) ||
				(queryType.compareTo("UPDATE") == 0) ||
				(queryType.compareTo("START") == 0) ||
				(queryType.compareTo("COMMIT") == 0) ||
				(queryType.compareTo("ROLLBACK") == 0))
			{
				st.execute(sql);
			}
			else
			{
				rs = st.executeQuery(sql);
				rsm = rs.getMetaData();
				if (logStatement) 
				{
					if ((rs.getType() == ResultSet.TYPE_SCROLL_INSENSITIVE) &&
						(rs.getConcurrency() == ResultSet.CONCUR_READ_ONLY))
					{
						rs.last();
						log.debug("Done. Retrived " + rs.getRow() + " rows");
						rs.beforeFirst();
					}
				}
			}
		}
		catch(SQLException e)
		{
			throw new WateringException(e.getMessage(), e.getErrorCode());
		}
		catch(Exception e)
		{
			log.warn("Exception " + e.getMessage() + " catched on statemet '" + sql + "'", e);		
			throw new WateringException(e);
		}
	}

	public ResultSetMetaData getRsm() {
		return rsm;
	}

	public ResultSet getRs() {
		return rs;
	}

	public Statement getSt()
	{
		return st;
	}
	
	public static DBConnection getInstance() throws WateringException
	{
		// Check if we're in test mode
		if (isTestMode()) {
			return MockDBConnection.getInstance();
		}

		if (singletonInstance == null || singletonInstance.conn == null || singletonInstance.st == null) {
			try {
				if (singletonInstance != null) {
					singletonInstance.finalize();
				}
			} catch (Throwable e) {
				// no actions
			}
			singletonInstance = new DBConnection();
		}
		
		singletonInstance.getConnection();
		return singletonInstance;
	}

    protected static boolean isTestMode() {
        String testMode = System.getenv("WATERING_TEST_MODE");
        return testMode != null && testMode.equals("true");
    }
}
