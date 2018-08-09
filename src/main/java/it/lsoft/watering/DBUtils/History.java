package it.lsoft.watering.DBUtils;

import java.util.Date;

public class History extends DBInterface
{
	public static final int TYPE_MOISTURE = 0;
	public static final int TYPE_WATERING_TIME = 1;
	public static final int TYPE_VALVE_CHANGE_STATUS = 2;
	public static final int TYPE_MOISTURE_AT_START = 3;
	public static final int TYPE_MOISTURE_AT_END = 4;
	
	private static final long serialVersionUID = -7844508274722236575L;
	
	protected int idHistory;
	protected int type;
	protected int unityId;
	protected double value;
	protected double readValue;
	protected double rangeFrom;
	protected double rangeTo;
	protected Date timestamp;
	
	public History()
	{
		tableName = "History";
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public int getUnityId() {
		return unityId;
	}

	public void setUnityId(int unityId) {
		this.unityId = unityId;
	}

	public double getValue() {
		return value;
	}

	public void setValue(double value) {
		this.value = value;
	}

	public Date getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Date timestamp) {
		this.timestamp = timestamp;
	}

	public int getIdHistory() {
		return idHistory;
	}

	public void setIdHistory(int idHistory) {
		this.idHistory = idHistory;
	}

	public double getReadValue() {
		return readValue;
	}

	public void setReadValue(double readValue) {
		this.readValue = readValue;
	}

	public double getRangeFrom() {
		return rangeFrom;
	}

	public void setRangeFrom(double rangeFrom) {
		this.rangeFrom = rangeFrom;
	}

	public double getRangeTo() {
		return rangeTo;
	}

	public void setRangeTo(double rangeTo) {
		this.rangeTo = rangeTo;
	}
}
