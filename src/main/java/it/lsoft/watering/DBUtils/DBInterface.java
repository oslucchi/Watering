package it.lsoft.watering.DBUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Utility;

public class DBInterface implements Serializable
{
	static final long serialVersionUID = 1;
	static final Logger log = Logger.getLogger(DBInterface.class);

	protected String tableName = "";
	protected String idColName = "";
	protected boolean readPage = false;
	protected boolean showNext = false;
	protected boolean showPrevious = false;
	protected int startRecord = 0;
	protected boolean changesToJournal = false;
	protected static boolean logStatement = true;
	
	private String quoteString(String strToQuote)
	{
		String strQuoted = strToQuote;
		int	offset = -1;
		int i = 0; 
		while(i >= 0)
		{
			if ((offset = strQuoted.indexOf("'", i)) >= 0)
			{
				strQuoted = strQuoted.substring(0, offset) + "'" + strQuoted.substring(offset);
				i = offset + 2;
			}
			else
			{
				i = -1;
			}
		}
		return strQuoted;
	}

	public static int numberOfRecords(DBConnection conn, String table, String where) throws Exception 
	{
		int count = -1;
		
		String sql = "SELECT COUNT(*) as count FROM " + table + " WHERE " + where;
    	conn.executeQuery(sql, false);
		ResultSet rs = conn.getRs();
		try 
		{
			if(rs.next())
				count = rs.getInt("count");
		}
		catch(SQLException e) 
		{
			throw e;
		}
		return(count);
	}
		
    protected static Field[] getAllFields(Class<?> cType)
	{
		List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = cType; c != null; c = c.getSuperclass()) 
        {
        	List<Field> tempList = new ArrayList<Field>();
        	tempList.addAll(Arrays.asList(c.getDeclaredFields()));
        	for(int y = tempList.size() - 1; y >= 0 ; y--)
        	{
        		if ((tempList.get(y).getModifiers() & java.lang.reflect.Modifier.PROTECTED) != java.lang.reflect.Modifier.PROTECTED)
        			tempList.remove(y);
        	}
        	if (tempList.size() > 0)
        		fields.addAll(tempList);
        }
        Field[] fieldArr = new Field[fields.size()];
        return fields.toArray(fieldArr);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public String getUpdateStatement(DBConnection conn, String sql, Object tbObj, String avoidColumn) throws Exception
	{
    	String sSep = "";
    	String[] avoidCols = null;
    	String retVal = null;
    	String retSql;

    	if (avoidColumn.indexOf(";") > 0)
    	{
			avoidCols = avoidColumn.split(";");
			for( int y = 0; y < avoidCols.length; y++)
			{
				avoidCols[y] = avoidCols[y].trim();
			}
    	}
    	else
    	{
			avoidCols = new String[1];
			avoidCols[0] = avoidColumn;
    	}
    	conn.executeQuery(sql, false);

    	retSql = "";
		ResultSetMetaData rsm = conn.getRsm();
    	int y, columnCount;
		try
		{
			columnCount = rsm.getColumnCount();
		}
		catch(SQLException e) 
		{
			retSql = "Error " + e.getMessage() + " retrieving column count from result set. (" + Utility.printStackTrace(e) + ")"; 
			throw new Exception(retSql);
		}
    	
    	Field[] clFields = getAllFields(tbObj.getClass());
		for(int i = 1; i <= columnCount; i++)
		{
			String columnName;
			int columnType;
			try 
			{
				columnType = rsm.getColumnType(i);
				columnName = rsm.getColumnLabel(i);
			}
			catch(SQLException e) 
			{
				retSql = "Error " + e.getMessage() + " retrieving column details. (" + Utility.printStackTrace(e) + ")"; 
				throw new Exception(retSql);
			}
			
			// Check if the column is in the avoid pool
			for(y = 0; y < avoidCols.length; y++)
			{
				if(columnName.compareTo(avoidCols[y]) == 0)
				{
					break;
				}
			}
			if (y < avoidCols.length)
			{
				continue;
			}

			for(y = 0; y < clFields.length; y++)
			{
				if (clFields[y].getName().compareTo(columnName) == 0)
				{
					try
					{
						switch(columnType)
						{
						case Types.INTEGER:
						case Types.BIGINT:
						case Types.SMALLINT:
						case Types.TINYINT:
						case Types.NUMERIC:
						case Types.BIT:
							switch(clFields[y].getType().getName())
							{
							case "long":
								retSql += sSep + columnName + " = " + clFields[y].getLong(tbObj);
								break;
							case "boolean":
								retSql += sSep + columnName + " = " + clFields[y].getBoolean(tbObj);
								break;
							default:
								retSql += sSep + columnName + " = " + clFields[y].getInt(tbObj);
							}
							sSep = ", ";
							break;
							
						case Types.DATE:
						case Types.TIMESTAMP:
							String dataFmt = "";
							try {
								Class[] signature = new Class[1];
								Class cObj = Class.forName(tbObj.getClass().getName());
								signature[0] = Class.forName(clFields[y].getType().getName());
								Method mtGet = cObj.getMethod("get" + clFields[y].getName().substring(0, 1).toUpperCase() + 
															  clFields[y].getName().substring(1) + "_fmt",(Class[]) null);
								dataFmt = (String)mtGet.invoke(tbObj, (Object[]) null);
							}
							catch(Exception E)
							{
								dataFmt = "yyyy-MM-dd HH:mm:ss";
							}
	
							DateFormat df = new SimpleDateFormat(dataFmt);
							
							if (clFields[y].get(tbObj) == null)
							{
								retSql += sSep + columnName + " = NULL ";
							}
							else
							{
								retSql += sSep + columnName + " = '" + df.format(clFields[y].get(tbObj)) + "'";
							}
							sSep = ", ";
							break;
	
						case Types.BLOB:
						case Types.CHAR:
						case Types.VARCHAR:
						case Types.LONGVARCHAR:
							// log.debug("setting '" + clFields[y].getName() +"' to '" + clFields[y].get(tbObj) + "'");
							if (clFields[y].get(tbObj) == null)
							{
								retSql += sSep + columnName + " = NULL ";
							}
							else
							{
								retSql += sSep + columnName + " = '" + quoteString(String.valueOf(clFields[y].get(tbObj))) + "'";
							}
							sSep = ", ";
							break;
						
						case Types.FLOAT:
						case Types.REAL:
						case Types.DOUBLE:
						case Types.DECIMAL:
							try
							{
								retSql += sSep + columnName + " = " + clFields[y].getDouble(tbObj);
								sSep = ", ";
							}
							catch(Exception e)
							{
								log.warn("Exception setting " + columnName + " to duoble", e);
							}
							break;
							
						default:
							if (clFields[y].get(tbObj) == null)
							{
								retSql += sSep + columnName + " = NULL ";
							}
							else
							{
								retSql += sSep + columnName + " = '" + quoteString(String.valueOf(clFields[y].get(tbObj))) + "'";
							}
							sSep = ", ";
						}
					}
					catch(Exception e)
					{ 
						log.warn("Exception " + e.getMessage(), e);
			    		retVal = "Error " + e.getMessage() + " retrieving fields value from object (" + Utility.printStackTrace(e) + ")";
						throw new Exception(retVal);
					}
					// No need to proceed further looking for other columns
					y = clFields.length + 1;
				}						
			}
		}
		return retSql;
	}
	
	// Get a where clause based on the specified columns
	// Date fields should have a method 'getAttributeName_fmt()' otherwise it will default to 'yyyy-MM-dd HH:mm:ss' 
	private String getWhereClauseOnId(String onColumns) throws Exception
	{
		String whereClause = "WHERE ";
		String sep = "";
    	Field[] clFields = null;

    	clFields = getAllFields(this.getClass());
		try 
		{
	    	for(String fieldName: onColumns.split(";"))
	    	{
				for(int y = 0; y < clFields.length; y++)
				{
					if (clFields[y].getName().compareTo(fieldName) == 0)
					{
						if (clFields[y].getType().getName().compareTo("int") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getInt(this);
						}
						else if (clFields[y].getType().getName().compareTo("long") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getLong(this);
						}
						else if (clFields[y].getType().getName().compareTo("float") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getFloat(this);
						}
						else if (clFields[y].getType().getName().compareTo("double") == 0)
						{
							whereClause += sep + fieldName + " = " + clFields[y].getDouble(this);
						}
						else if (clFields[y].getType().getName().compareTo("char") == 0)
						{
							whereClause += sep + fieldName + " = '" + clFields[y].getChar(this) + "'";
						}
						else if (clFields[y].getType().getName().compareTo("java.lang.String") == 0)
						{
							whereClause += sep + fieldName + " = '" + clFields[y].get(this) + "'";
						}
						else if (clFields[y].getType().getName().compareTo("java.util.Date") == 0)
						{
							String dataFmt = "";
							try 
							{
								Class<?>[] signature = new Class[1];
								Class<?> cObj = Class.forName(this.getClass().getName());
								signature[0] = Class.forName(clFields[y].getType().getName());
								Method mtGet = cObj.getMethod("get" + clFields[y].getName().substring(0, 1).toUpperCase() + 
															  clFields[y].getName().substring(1) + "_fmt",(Class[]) null);
								dataFmt = (String)mtGet.invoke(this, (Object[]) null);
							}
							catch(Exception e)
							{
								dataFmt = "yyyy-MM-dd HH:mm:ss";
							}
	
							DateFormat df = new SimpleDateFormat(dataFmt);
							if (clFields[y].get(this) != null)
							{
								whereClause += sep + fieldName + " = '" + df.format(clFields[y].get(this)) + "'";
							}
						}
						
						if (whereClause.compareTo("WHERE ") != 0)
						{
							sep = " AND ";
						}
						break;
					}
				}
	    	}
		}
		catch(Exception e) 
		{
			throw new Exception(e);
		}
		return(whereClause);
	}
	
    private static void populateObjectAttributesFromRecordset(Object objInst, ResultSetMetaData rsm, ResultSet rs) 
			throws Exception
	{
		Field[] clFields = getAllFields(objInst.getClass());
		for(int i = 1; i <= rsm.getColumnCount(); i++)
		{
			for(int y = 0; y < clFields.length; y++)
			{
				if (clFields[y].getName().compareTo(rsm.getColumnLabel(i)) == 0)
				{
					switch(rsm.getColumnType(i))
					{
					case Types.INTEGER:
					case Types.BIGINT:
					case Types.SMALLINT:
					case Types.TINYINT:
					case Types.NUMERIC:
					case Types.BIT:
						switch(clFields[y].getType().getName())
						{
						case "boolean":
							clFields[y].setBoolean(objInst, rs.getBoolean(clFields[y].getName()));
							break;
						default:
							clFields[y].setInt(objInst, rs.getInt(clFields[y].getName()));
						}
						break;
						
					case Types.DATE:
						try
						{
							clFields[y].set(objInst, rs.getDate(clFields[y].getName()));
						}
						catch(SQLException e1)
						{
							clFields[y].set(objInst, null);
						}
						break;

					case Types.TIMESTAMP:
						try
						{
							clFields[y].set(objInst, rs.getTimestamp(clFields[y].getName()));
						}
						catch(SQLException e1)
						{
							clFields[y].set(objInst, null);
						}
						break;

					case Types.BLOB:
					case Types.CHAR:
					case Types.VARCHAR:
					case Types.LONGVARCHAR:
						clFields[y].set(objInst, rs.getString(clFields[y].getName()));
						break;
					
					case Types.FLOAT:
					case Types.REAL:
					case Types.DOUBLE:
					case Types.DECIMAL:
						clFields[y].set(objInst, rs.getDouble(clFields[y].getName()));
						break;
						
					default:
						clFields[y].set(objInst, rs.getString(clFields[y].getName()));
						
					}							
					y = clFields.length + 1;
				}
			}
		}
	}
	
    private static ArrayList<Object> populateGenericObjectFromRecordset(ResultSetMetaData rsm, ResultSet rs) 
			throws Exception
	{
		ArrayList<Object> retVal = new ArrayList<>();
		for(int i = 1; i <= rsm.getColumnCount(); i++)
		{
			switch(rsm.getColumnType(i))
			{
			case Types.INTEGER:
			case Types.BIGINT:
			case Types.SMALLINT:
			case Types.TINYINT:
			case Types.NUMERIC:
			case Types.BIT:
				retVal.add(new Integer(rs.getInt(rsm.getColumnName(i))));
				break;

			case Types.DATE:
				retVal.add(rs.getDate(rsm.getColumnName(i)));
				break;

			case Types.TIMESTAMP:
				retVal.add(rs.getTimestamp(rsm.getColumnName(i)));
				break;

			case Types.BLOB:
			case Types.CHAR:
			case Types.VARCHAR:
			case Types.LONGVARCHAR:
				retVal.add(rs.getString(rsm.getColumnName(i)));
				break;

			case Types.FLOAT:
			case Types.REAL:
			case Types.DOUBLE:
			case Types.DECIMAL:
				retVal.add(rs.getDouble(rsm.getColumnName(i)));
				break;

			default:
				retVal.add(rs.getString(rsm.getColumnName(i)));
			}							
		}
		return retVal;
	}

    public void populateObject(DBConnection conn, String sql, Object tbObj) throws Exception
	{
		conn.executeQuery(sql, logStatement);
		if (conn.getRs().next())
		{
	    	populateObjectAttributesFromRecordset(tbObj, conn.getRsm(), conn.getRs());
		}
		else
		{
			throw new Exception("No record found");	
		}
	}

    /*
     * It works only for numeric id... the sql string will be invalid otherwise
     */
    public void populateObject(DBConnection conn, String idName) throws Exception
	{
		String sql = "SELECT * FROM " + tableName + " WHERE " + idName + " = ";
		sql += this.getClass().getDeclaredField(idName).get(this);
		populateObject(conn, sql, this);
	}
	
	public static Object populateByQuery(String sql, Class<?> objClass) throws Exception
	{
    	String retVal = null;
		Object objInst = objClass.newInstance();
    	
		DBConnection conn = null;
		try
		{
			conn = connect();
	    	conn.executeQuery(sql, logStatement);
			if(conn.getRs().next())
			{
				populateObjectAttributesFromRecordset(objInst, conn.getRsm(), conn.getRs());
				return objInst;
			}
		}
		catch(Exception e) 
		{
    		retVal = "Error '" + e.getMessage() + "' retrieving fields from class '" + 
					  objClass.getName() + "'" + ". StackTrace:" + Utility.printStackTrace(e);
			throw new Exception(retVal);
		}
		finally
		{
			DBInterface.disconnect(conn);
		}
		throw(new Exception("No record found"));
	}

    public static ArrayList<?> populateCollection(String sql, Class<?> objClass) throws Exception
	{
		return populateCollection(sql, logStatement, objClass);
	}

    public static ArrayList<?> populateCollection(String sql, boolean logStatement, Class<?> objClass) throws Exception
	{
    	String retVal = null;
    	ArrayList<Object> aList = new ArrayList<Object>();
    	
		DBConnection conn = null;
		try
		{
			conn = connect();
	    	conn.executeQuery(sql, logStatement);
			while(conn.getRs().next())
			{
				Object objInst = objClass.newInstance();
				populateObjectAttributesFromRecordset(objInst, conn.getRsm(), conn.getRs());
				aList.add(objInst);
			}
		}
		catch(Exception e) 
		{
    		retVal = "Error " + e.getMessage() + " retrieving fields from class '" + 
					  objClass.getName() + "'. (" + Utility.printStackTrace(e) + ")";
			throw new Exception(retVal);
		}
		finally
		{
			DBInterface.disconnect(conn);
		}
		return aList;
	}

	public ArrayList<?> populateCollectionOnCondition(String whereClause, Class<?> objClass) throws Exception
	{
		return(populateCollection("SELECT * FROM " + tableName + " " + whereClause, objClass));
	}
	
	public ArrayList<?> populateCollectionOfDistinctsOnCondition(String whereClause, String distinctColumn, Class<?> objClass) throws Exception
	{
		return(populateCollection("SELECT DISTINCT * " +
								  "FROM " + tableName + " " + 
								  whereClause + " " +
								  "GROUP BY " + distinctColumn, objClass));
	}

	protected ArrayList<String[]> getObjectChanges(Object oldInst, Object newInst) throws Exception
	{
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		ArrayList<String[]> changes = new ArrayList<String[]>();
		try
		{
	    	for (Field f : getAllFields(oldInst.getClass())) 
			{
				String[] locChanges = null;
	    		if (f.getType() == int.class)
	    		{
	    			if (f.getInt(oldInst) != f.getInt(newInst))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : Integer.toString(f.getInt(oldInst)));
	    				locChanges[2] = "" + f.getInt(newInst);
	    			}
	    		}
	    		else if (f.getType() == long.class)
	    		{
	    			if (f.getLong(oldInst) != f.getLong(newInst))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : Long.toString(f.getLong(oldInst)));
	    				locChanges[2] = "" + f.getLong(newInst);
	    			}
	    		}						
	    		else if (f.getType() == boolean.class)
	    		{
	    			if (f.getBoolean(oldInst) != f.getBoolean(newInst))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : Boolean.toString(f.getBoolean(oldInst)));
	    				locChanges[2] = "" + f.getBoolean(newInst);
	    			}
	    		}						
	    		else if (f.getType() == String.class)
	    		{
	    			Object o = f.get(oldInst);
	    			Object n = f.get(newInst);
	    			if (!((o == null) && (n == null)) &&
	    				(((o == null ) && (n != null)) || ((o != null) && (n == null)) ||
	    				 ((String) f.get(oldInst)).compareTo((String) f.get(newInst)) != 0))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (String) (oldInst == null ? "" : f.get(oldInst));
	    				locChanges[2] = (String) f.get(newInst);
	    			}
	    		}			
	    		else if (f.getType() == Date.class)
	    		{
	    			Object o = f.get(oldInst);
	    			Object n = f.get(newInst);
	    			if (!((o == null) && (n == null)) &&
	    				(((o == null ) && (n != null)) || ((o != null) && (n == null)) ||
	    				 !((Date) f.get(oldInst)).equals((Date) f.get(newInst))))
	    			{
	    				locChanges = new String[3];
	    				locChanges[0] = "" + f.getName();
	    				locChanges[1] = (oldInst == null ? "" : formatter.format(f.get(oldInst)));
	    				locChanges[2] = (newInst == null ? "" : formatter.format(f.get(newInst)));
					}
				}
	    		
	    		if (locChanges != null)
	    		{
	    			changes.add(locChanges);
	    		}
			}
		}
		catch(Exception e)
		{
			throw new Exception(e);
		}
		return(changes);
	}
	
	
	public void update(DBConnection conn, String avoidColumns, String whereClause) throws Exception
	{
    	String sql = null;

    	/*
    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
    	 */
		String sqlQueryColNames = "SELECT * FROM " + tableName + " WHERE 1 = 0";
		sql = "UPDATE " + tableName + " SET ";
		sql += this.getUpdateStatement(conn, sqlQueryColNames, this, avoidColumns);
		sql += " " + whereClause;
		conn.executeQuery(sql, logStatement);
    }

	public void update(DBConnection conn, String idColumns) throws Exception
	{
		update(conn, idColumns, getWhereClauseOnId(idColumns));
    }

	public static void updateCollection(ArrayList<?> collection, String idColName, Class<?> objectClass) 
			throws Exception
	{
		if (collection.size() == 0)
			return;
    	String sql = "";
		String tableName = ((DBInterface) collection.get(0)).tableName;
		String sqlQueryColNames = "SELECT * FROM " + tableName + " WHERE 1 = 0";
		DBConnection conn = connect();
		try 
		{
	    	for(int i = 0; i < collection.size(); i++)
	    	{
		    	/*
		    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
		    	 */
				sql = "UPDATE " + tableName + " SET ";
				sql += (((DBInterface) collection.get(i)))
						.getUpdateStatement(conn, sqlQueryColNames, collection.get(i), idColName);
				// Get the id of the current object 
				Class<?> c = collection.get(i).getClass();
				Method m;
					m = c.getMethod("get" + idColName.substring(0,1).toUpperCase() + idColName.substring(1), new Class[] {});
					sql += " WHERE " + idColName + " = " + ((Integer) m.invoke(collection.get(i))).intValue();
					conn.executeQuery(sql, logStatement);
	    	}
		}
		catch(Exception e) 
		{
			throw new Exception(e);
		}
		finally
		{
			DBInterface.disconnect(conn);
		}
	}

	public void insert(DBConnection conn , String idColName, Object objectToInsert) throws Exception
	{
    	String sql = "";
    	/*
    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
    	 */
		String sqlQueryColNames = "SELECT * FROM " + ((DBInterface) objectToInsert).tableName + " WHERE 1 = 0";	    	
		sql = "INSERT INTO " + ((DBInterface) objectToInsert).tableName + " SET ";
		sql += this.getUpdateStatement(conn, sqlQueryColNames, objectToInsert, idColName);
		conn.executeQuery(sql, logStatement);
	}

	public int insertAndReturnId(DBConnection conn, String idColName, Object objectToInsert) throws Exception
	{
		int id = -1;
		insert(conn, idColName, objectToInsert);
		conn.executeQuery("SELECT LAST_INSERT_ID() AS id", logStatement);
		if (conn.getRs().next())
		{
			id = conn.getRs().getInt("id");
		}
		return(id);
	}

	public static void insertCollection(ArrayList<?> collection, String idColName, Class<?> objectClass) 
			throws Exception
	{
		if (collection.size() == 0)
			return;
    	String sql = "";
		String tableName = ((DBInterface) collection.get(0)).tableName;
		String sqlQueryColNames = "SELECT * FROM " + tableName + " WHERE 1 = 0";
		DBConnection conn = connect();
		try
		{
	    	for(int i = 0; i < collection.size(); i++)
	    	{
		    	/*
		    	 * Populating a ResultSetMetaData object to obtain table columns to be used in the query.
		    	 */
				sql = "INSERT INTO " + tableName + " SET ";
				sql += (((DBInterface) collection.get(i)))
						.getUpdateStatement(conn, sqlQueryColNames, collection.get(i), idColName);
				conn.executeQuery(sql, logStatement);
	    	}
		}
    	catch(Exception e)
		{
			throw e;
		}
		finally
		{
			DBInterface.disconnect(conn);
		}
	}

	public void delete(DBConnection conn, String sql) throws Exception
	{
		conn.executeQuery(sql, logStatement);
	}
		
	public void delete(DBConnection conn, int id) throws Exception
	{
		conn.executeQuery("DELETE FROM " + tableName + " WHERE " + idColName + " = " + id, logStatement);
	}

	public static void executeStatement(DBConnection conn, String sql, boolean inTransaction) 
			throws Exception 
	{
    	try
    	{
    		conn.executeQuery(sql, logStatement);
		}
		catch(Exception e) 
		{
			throw e;
		}
	}
	
	public static ArrayList<Object> executeAggregateStatement(DBConnection conn, String sql) throws Exception
	{
		ArrayList<Object> retVal = null;
		conn.executeQuery(sql, logStatement);
		if (conn.getRs().next())
		{
			retVal = populateGenericObjectFromRecordset(conn.getRsm(), conn.getRs());
		}
		return retVal;
	}
	
	public static DBConnection TransactionStart() throws Exception
	{
		DBConnection conn = DBConnection.getInstance();
		try
		{
			conn.executeQuery("START TRANSACTION", logStatement);
		}
    	catch(Exception e)
		{
    		log.warn("Exception " + e.getMessage(), e);
			DBInterface.disconnect(conn);
			throw e;
		}
    	return conn;
	}

	public static void TransactionStart(DBConnection conn ) throws Exception
	{
		conn.executeQuery("START TRANSACTION", logStatement);
	}

	public static void TransactionCommit(DBConnection conn) throws Exception 
	{
		conn.executeQuery("COMMIT", logStatement);
	}

	public static void TransactionRollback(DBConnection conn) 
	{
		try 
		{
			conn.executeQuery("ROLLBACK", logStatement);
		}
		catch(Exception e)
		{
			log.warn("Exception " + e.getMessage(), e);
		}
	}
	
	public static DBConnection connect() throws Exception
	{
		return DBConnection.getInstance();
	}
	
	public static void disconnect(DBConnection conn) 
	{
		if (conn != null)
			conn.finalize();
	}

	public boolean isLogStatement() {
		return logStatement;
	}

	public void setLogStatement(boolean logStatement) {
		DBInterface.logStatement = logStatement;
	}
}