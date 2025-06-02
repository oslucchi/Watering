package it.lsoft.watering.Commons;

public class JsonResponse {
    public enum Status {
        OK,
        NOK
    }

    private Status status;
    private int msgLen;
    private byte[] data;

    public JsonResponse() {
        // Default constructor for JSON serialization
    }

    public JsonResponse(Status status, byte[] data) {
        this.status = status;
        this.data = data;
        this.msgLen = data != null ? data.length : 0;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getMsgLen() {
        return msgLen;
    }

    public void setMsgLen(int msgLen) {
        this.msgLen = msgLen;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.msgLen = data != null ? data.length : 0;
    }
} 