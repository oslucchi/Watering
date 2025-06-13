package it.lsoft.watering.Commons;

import java.util.Date;
import java.util.Objects;

public class Status {
	public static final int FLG_WATERING = 0;
	public static final int FLG_DISABLE = 1;
	public static final int FLG_SUSPEND = 2;
	public static final int FLG_SKIP = 3;
	public static final int FLG_FORCE = 4;
	public static final int FLG_AUTOSKIP = 5;
	public static final int FLG_SENSOR_DUMP = 6;
	public static final int FLG_MODE = 7;

	public static final int MNGD_FLAGS = 8;
	
	private Date nextStart = null;
	private double[] moisture = null;
	private boolean[] watering = null;
	private int[] curWateringTime = null;
	private int[] expWateringTime = null;
	private int versionId = 0;
	private boolean[] flags = new boolean[MNGD_FLAGS];
	private int currentArea = -1;
	
	public Status(Parameters parms) {
		moisture = new double[parms.getNumberOfSensors()];
		watering = new boolean[parms.getZones()];
		curWateringTime = new int[parms.getZones()];
		expWateringTime = new int[parms.getZones()];
	}

	@Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Status)) return false;
        Status other = (Status) o;
        return Objects.equals(nextStart, other.nextStart) &&
         	   Objects.equals(moisture, other.moisture) &&
         	   Objects.equals(watering, other.watering) &&
         	   Objects.equals(curWateringTime, other.curWateringTime) &&
         	   Objects.equals(expWateringTime, other.expWateringTime) &&
         	   Objects.equals(currentArea, other);
    }
    
    public Date getNextStart() {
		return nextStart;
	}

	public void setNextStart(Date nextStart) {
		this.nextStart = nextStart;
	}

	public double[] getMoisture() {
		return moisture;
	}

	public void setMoisture(double[] moisture) {
		this.moisture = moisture;
	}

	public boolean[] getWatering() {
		return watering;
	}

	public void setWatering(boolean[] watering) {
		this.watering = watering;
	}

	public int[] getCurWateringTime() {
		return curWateringTime;
	}

	public void setCurWateringTime(int[] curWateringTime) {
		this.curWateringTime = curWateringTime;
	}

	public int[] getExpWateringTime() {
		return expWateringTime;
	}

	public void setExpWateringTime(int[] expWateringTime) {
		this.expWateringTime = expWateringTime;
	}

	public boolean[] getFlags() {
		return flags;
	}

	public void setFlags(boolean[] flags) {
		this.flags = flags;
	}

	public int getVersionId() {
		return versionId;
	}

	public void setVersionId(int versionId) {
		this.versionId = versionId;
	}

	public int getCurrentArea() {
		return currentArea;
	}

	public void setCurrentArea(int currentArea) {
		this.currentArea = currentArea;
	}
}
