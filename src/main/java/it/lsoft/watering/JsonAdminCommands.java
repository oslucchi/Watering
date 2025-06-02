package it.lsoft.watering;

import com.google.gson.Gson;
import it.lsoft.watering.Commons.JsonCommand;
import it.lsoft.watering.Commons.JsonResponse;
import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.Raspberry.RealTimeData;
import it.lsoft.watering.Raspberry.IWateringHandler;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.log4j.Logger;

public class JsonAdminCommands extends Thread implements IWateringHandler {
    private static final int JSON_PORT = 9899;
    private ServerSocket serverSocket = null;
    private final RealTimeData rtData;
    private final Parameters parms;
    private final Gson gson;
    private static final Logger logger = Logger.getLogger(JsonAdminCommands.class);

    public JsonAdminCommands(RealTimeData rtData) {
        this.rtData = rtData;
        this.parms = rtData.getParms();
        this.gson = new Gson();
        
        try {
            serverSocket = new ServerSocket(JSON_PORT);
            logger.info("JSON TCP interface listening on port " + JSON_PORT);
        } catch (IOException e) {
            logger.fatal("Exception " + e.getMessage() + " on JSON ServerSocket creation");
            rtData.setShutDown(true);
        }
        logger.info("Initialized JSON Admin Commands");
    }

    @Override
    public void run() {
        logger.debug("JSON Admin Commands thread started");
        while (!rtData.isShutDown()) {
            try {
                Socket clientSocket = serverSocket.accept();
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                String input = reader.readLine();
                if (input != null) {
                    JsonCommand command = gson.fromJson(input, JsonCommand.class);
                    JsonResponse response = executeCommand(command);
                    writer.write(gson.toJson(response) + "\n");
                    writer.flush();
                }
                clientSocket.close();
            } catch (IOException e) {
                logger.error("Error in JSON admin commands: " + e.getMessage());
            }
        }
    }

    private JsonResponse executeCommand(JsonCommand command) {
        if (command == null || command.getCommand() == null) {
            return new JsonResponse(JsonResponse.Status.NOK, "Invalid command format".getBytes());
        }

        try {
            switch (command.getCommand().toLowerCase()) {
                case "status":
                    StringBuilder status = new StringBuilder();
                    if (rtData.getMode().compareTo("manual") == 0) {
                        status.append("The system is running in manual mode\n");
                    } else {
                        status.append("Next startup at: ").append(rtData.getNextStartTime()).append("\n");
                    }
                    status.append("Humidity from sensors:\n");
                    for (int i = 0; i < parms.getNumberOfSensors(); i++) {
                        status.append(String.format("Sensor %d %2.2f\n", i, rtData.getMoisture(i)));
                    }
                    status.append("Watering is ").append(rtData.getInCycle() < 0 ? "inactive" : "active on zone " + rtData.getInCycle()).append("\n");
                    if (rtData.getInCycle() >= 0) {
                        for (int i = 0; i < parms.getZones(); i++) {
                            status.append(String.format("Zone %d %s%s\n", 
                                i, 
                                rtData.getValveStatus(i) ? "watering" : "off",
                                rtData.getValveStatus(i) ? " since " + rtData.getWateringTimeElapsed(i) + " sec" : ""));
                        }
                    }
                    status.append("Disable flag is   : ").append(rtData.isDisableFlag()).append("\n");
                    status.append("Suspend flag is   : ").append(rtData.isSuspendFlag()).append("\n");
                    status.append("Skip flag is      : ").append(rtData.isSkipCycleFlag()).append("\n");
                    status.append("Force flag is     : ").append(rtData.isForceManual()).append("\n");
                    status.append("AutoSkip flag is  : ").append(parms.isEnableAutoSkip()).append("\n");
                    status.append("Error code is     : ").append(rtData.getErrorCode()).append("\n");
                    status.append("Sensor dumping is : ").append(rtData.getParms().isDumpSensorReading()).append("\n");
                    if (rtData.getDelayByMinutes() > 0) {
                        status.append("Start delayed by: ").append(rtData.getDelayByMinutes()).append(" min\n");
                    }
                    return new JsonResponse(JsonResponse.Status.OK, status.toString().getBytes());

                case "disable":
                    rtData.setDisableFlag(true);
                    return new JsonResponse(JsonResponse.Status.OK, null);

                case "enable":
                    rtData.setDisableFlag(false);
                    String nextStart = "Re-evaluated next start time to " + rtData.getNextStartTime();
                    return new JsonResponse(JsonResponse.Status.OK, nextStart.getBytes());

                case "suspend":
                    rtData.setSuspendFlag(true);
                    return new JsonResponse(JsonResponse.Status.OK, 
                        ("Suspend flag is now: " + rtData.isSuspendFlag()).getBytes());

                case "resume":
                    rtData.setSuspendFlag(false);
                    return new JsonResponse(JsonResponse.Status.OK, 
                        ("Suspend flag is now: " + rtData.isSuspendFlag()).getBytes());

                case "startman":
                    if (command.getParameters() == null || command.getParameters().length == 0) {
                        return new JsonResponse(JsonResponse.Status.NOK, "Schedule parameter required".getBytes());
                    }
                    try {
                        int schedule = Integer.parseInt(command.getParameters()[0].toString());
                        if (schedule < 0 || schedule > parms.getNumberOfSchedules()) {
                            return new JsonResponse(JsonResponse.Status.NOK, 
                                ("Invalid schedule. Value permitted from 0 to " + parms.getNumberOfSchedules()).getBytes());
                        }
                        rtData.setForceManual(true);
                        rtData.setScheduleIndex(schedule);
                        return new JsonResponse(JsonResponse.Status.OK, null);
                    } catch (NumberFormatException e) {
                        return new JsonResponse(JsonResponse.Status.NOK, 
                            ("Malformatted schedule parameter: " + command.getParameters()[0]).getBytes());
                    }

                default:
                    return new JsonResponse(JsonResponse.Status.NOK, "Unknown command".getBytes());
            }
        } catch (Exception e) {
            logger.error("Error executing command: " + e.getMessage(), e);
            return new JsonResponse(JsonResponse.Status.NOK, ("Error: " + e.getMessage()).getBytes());
        }
    }
} 