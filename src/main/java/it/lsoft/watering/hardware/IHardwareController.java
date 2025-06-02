package it.lsoft.watering.hardware;

public interface IHardwareController {
    void setValveStatus(int valveIndex, boolean status);
    void setPumpStatus(boolean status);
    Integer readMoisture(int sensorId);
    Integer readSensorValue(int sensorId);
    void shutdown();
    boolean isError();
    String getErrorMessage();
} 