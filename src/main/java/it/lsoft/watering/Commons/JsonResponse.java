package it.lsoft.watering.Commons;

public class JsonResponse {
    public enum Status {
        OK,
        NOK
    }

    private Status status;
    private String data;

    public JsonResponse(Status status, String data) {
        this.status = status;
        this.data = data;
    }

    public Status getStatus() {
        return status;
    }

    public String getData() {
        return data;
    }
} 