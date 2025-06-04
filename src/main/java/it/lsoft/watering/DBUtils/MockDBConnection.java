package it.lsoft.watering.DBUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import org.apache.log4j.Logger;

public class MockDBConnection extends DBConnection {
    private static final Logger logger = Logger.getLogger(MockDBConnection.class);
    private static MockDBConnection instance = null;

    protected MockDBConnection() {
        logger.info("Initialized Mock DB Connection");
    }

    @Override
    public void getConnection() {
        logger.debug("Mock: getConnection() called - no actual connection in test mode");
    }

    @Override
    public void executeQuery(String sql, boolean logStatement) {
        if (logStatement) {
            logger.debug("Mock: executeQuery() called with SQL: " + sql);
        }
    }

    @Override
    protected void finalize() {
        logger.debug("Mock: finalize() called - no actual cleanup needed in test mode");
    }

    @Override
    public ResultSet getRs() {
        logger.debug("Mock: getRs() called - returning null in test mode");
        return null;
    }

    @Override
    public ResultSetMetaData getRsm() {
        logger.debug("Mock: getRsm() called - returning null in test mode");
        return null;
    }

    @Override
    public Statement getSt() {
        logger.debug("Mock: getSt() called - returning null in test mode");
        return null;
    }

    public static MockDBConnection getInstance() {
        if (instance == null) {
            instance = new MockDBConnection();
        }
        return instance;
    }
} 