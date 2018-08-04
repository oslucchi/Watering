package it.lsoft.watering.Commons;

public class Errors {
	public static final int NO_ERROR = 0;
	public static final int SENSOR_HANDLE_ERROR = 1;
	public static final int VALVE_HANDLE_ERROR = 2;
	public static final int PUMP_HANDLE_ERROR = 4;
	public static final int ARCHIVE_HANDLE_ERROR = 4;
	public static final int DB_CONNECTION_ERROR = 1 << 8;
	public static final int DATA_FILE_WRITE_ERROR = 2 << 8;
	public static final int READ_SENSOR_ERROR = 1 << 16;
}
