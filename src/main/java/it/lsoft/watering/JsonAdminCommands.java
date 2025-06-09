package it.lsoft.watering;

import com.google.gson.Gson;
import it.lsoft.watering.Commons.JsonCommand;
import it.lsoft.watering.Commons.JsonResponse;
import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.Commons.Status;
import it.lsoft.watering.Raspberry.RealTimeData;
import it.lsoft.watering.Raspberry.IWateringHandler;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.net.ServerSocket;
import java.net.Socket;
import org.apache.log4j.Logger;

public class JsonAdminCommands extends Thread implements IWateringHandler {
    private static final int JSON_PORT = 9899;
    private ServerSocket serverSocket = null;
    private final RealTimeData rtData;
    private final Parameters parms;
    private final Gson gson;
    private Status status = null;

    private static final Logger logger = Logger.getLogger(JsonAdminCommands.class);

    public JsonAdminCommands(RealTimeData rtData) {
        this.rtData = rtData;
        this.parms = rtData.getParms();
    	status = new Status(parms);
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
	public boolean isInitialized() {
	    return true;
	}


    @Override
    public void run() {
        logger.debug("JSON Admin Commands thread started");
        while (!rtData.isShutDown()) {
            try {
                Socket clientSocket = serverSocket.accept();
                logger.debug("JSON Admin Commands thread accepted connection from " + clientSocket.getInetAddress().getHostAddress());
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                String input = reader.readLine();
                if (input != null) {
                    logger.debug("JSON Admin Commands thread received command: '" + input + "'");
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
            return new JsonResponse(JsonResponse.Status.NOK, "Invalid command format");
        }

        try {
            logger.debug("JSON Admin Commands thread received command: '" + 
                            command.getCommand().toLowerCase() + 
                            "' with parameters: " + command.getParameters());
            switch (command.getCommand().toLowerCase()) {
                case "configshow":
                    try {
                        String content = new String(Files.readAllBytes(Paths.get(Parameters.getConfFilePath())));
                        return new JsonResponse(JsonResponse.Status.OK, content);
                    } catch (IOException e) {
                        logger.error("Error reading configuration file: " + e.getMessage());
                        return new JsonResponse(JsonResponse.Status.NOK, 
                            "Error reading configuration file: " + e.getMessage());
                    }

                case "configsave":
                    if (command.getParameters() == null || command.getParameters().length == 0) {
                        return new JsonResponse(JsonResponse.Status.NOK, "Configuration data required");
                    }
                    try {
                        // Create backup with timestamp
                        SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");
                        String timestamp = sdf.format(new Date());
                        String backupFile = Parameters.getConfFilePath() + "." + timestamp;
                        
                        // Backup existing file
                        Files.copy(Paths.get(Parameters.getConfFilePath()), Paths.get(backupFile), StandardCopyOption.REPLACE_EXISTING);
                        logger.info("Created backup file: " + backupFile);
                        
                        // Save new configuration
                        String newConfig = command.getParameters()[0].toString();
                        Files.write(Paths.get(Parameters.getConfFilePath()), newConfig.getBytes());
                        
                        // Reload configuration
                        rtData.setParms(parms.rescan());
                        
                        return new JsonResponse(JsonResponse.Status.OK, 
                            "Configuration saved and reloaded. Backup created: " + backupFile);
                    } catch (IOException e) {
                        logger.error("Error saving configuration file: " + e.getMessage());
                        return new JsonResponse(JsonResponse.Status.NOK, 
                            "Error saving configuration file: " + e.getMessage());
                    }

                case "status":
                	Status s = new Status(parms);
                    boolean [] flags = s.getFlags();                    
                    int[] curWateringTime = s.getCurWateringTime();
                    Arrays.fill(curWateringTime , 0);
                    int[] expWateringTime = curWateringTime;
                    
                    flags[Status.FLG_MODE] = (rtData.getMode().compareTo("manual") == 0);

                    boolean[] watering = s.getWatering();
                    Calendar cal = Calendar.getInstance();
            		cal.setTime(new Date());
            		int dayOfTheWeek = cal.get(Calendar.DAY_OF_WEEK);
            		
                    for(int i = 0; i < watering.length; i++)
                    {
                        flags[Status.FLG_WATERING] |= rtData.getValveStatus(i);;
                    	watering[i] = rtData.getValveStatus(i);
                    	if (watering[i])
                        {
                    		curWateringTime[i] = rtData.getWateringTimeElapsed(i);
                        }
                    	else
                    	{
                    		curWateringTime[i] = 0;
                    	}
                        expWateringTime[i] = parms.getZoneDuration(i, rtData, dayOfTheWeek);
                    }
                    s.setCurWateringTime(curWateringTime);
                    s.setExpWateringTime(expWateringTime);
                    
                    double[] moistures = s.getMoisture();
                    for (int i = 0; i < parms.getNumberOfSensors(); i++) {
                        moistures[i] = rtData.getMoisture(i);
                    }
                    s.setMoisture(moistures);
                    
                    flags[Status.FLG_DISABLE] = rtData.isDisableFlag(); 
                    flags[Status.FLG_SUSPEND] = rtData.isSuspendFlag();
					flags[Status.FLG_SKIP] = rtData.isSkipCycleFlag();
					flags[Status.FLG_FORCE] = rtData.isForceManual();
					flags[Status.FLG_AUTOSKIP] = parms.isEnableAutoSkip();
					flags[Status.FLG_SENSOR_DUMP] = rtData.getParms().isDumpSensorReading();                    
                    s.setFlags(flags);
                    
                    if (!status.equals(s))
                    {
                    	s.setVersionId(status.getVersionId() + 1);
                    	status = s;
                    }
                    /*
                    StringBuilder status = new StringBuilder();                    

                    if (rtData.getMode().compareTo("manual") == 0) {
                        status.append("The system is running in manual mode\n");
                    } else {
                        status.append("Next startup at: ").append(rtData.getNextStartTime()).append("\n");
                    }
                    status.append("Humidity from sensors:\n");
                    for (int i = 0; i < parms.getNumberOfSensors(); i++) {
                        status.append("Sensor ").append(i).append(" ")
                              .append(String.format("%2.2f", rtData.getMoisture(i))).append("\n");
                    }
                    status.append("Watering is ")
                          .append(rtData.getInCycle() < 0 ? "inactive\n" : "active on zone " + rtData.getInCycle() + ":\n");
                    if (rtData.getInCycle() >= 0) {
                        for (int i = 0; i < parms.getZones(); i++) {
                            status.append("Zone ").append(i).append(" ")
                                  .append(rtData.getValveStatus(i) ? "watering" : "   off  ")
                                  .append(rtData.getValveStatus(i) ? " since " + rtData.getWateringTimeElapsed(i) + " sec" : "")
                                  .append("\n");
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
                    */
                    return new JsonResponse(JsonResponse.Status.OK, gson.toJson(status));

                case "disable":
                    rtData.setDisableFlag(true);
                    return new JsonResponse(JsonResponse.Status.OK, null);

                case "enable":
                    rtData.setDisableFlag(false);
                    String nextStart = "Re-evaluated next start time to " + rtData.getNextStartTime();
                    return new JsonResponse(JsonResponse.Status.OK, nextStart);

                case "suspend":
                    rtData.setSuspendFlag(true);
                    return new JsonResponse(JsonResponse.Status.OK, 
                        "Suspend flag is now: " + rtData.isSuspendFlag());

                case "resume":
                    rtData.setSuspendFlag(false);
                    return new JsonResponse(JsonResponse.Status.OK, 
                        "Suspend flag is now: " + rtData.isSuspendFlag());

                case "startman":
                    if (command.getParameters() == null || command.getParameters().length == 0) {
                        return new JsonResponse(JsonResponse.Status.NOK, "Schedule parameter required");
                    }
                    try {
                        int schedule = Integer.parseInt(command.getParameters()[0].toString());
                        if (schedule < 0 || schedule > parms.getNumberOfSchedules()) {
                            return new JsonResponse(JsonResponse.Status.NOK, 
                                "Invalid schedule. Value permitted from 0 to " + parms.getNumberOfSchedules());
                        }
                        rtData.setForceManual(true);
                        rtData.setScheduleIndex(schedule);
                        return new JsonResponse(JsonResponse.Status.OK, null);
                    } catch (NumberFormatException e) {
                        return new JsonResponse(JsonResponse.Status.NOK, 
                            "Malformatted schedule parameter: " + command.getParameters()[0]);
                    }

                case "mode":
                    if (command.getParameters() == null || command.getParameters().length == 0) {
                        return new JsonResponse(JsonResponse.Status.NOK, "Mode parameter required (a/m)");
                    }
                    String mode = command.getParameters()[0].toString();
                    switch (mode) {
                        case "a":
                            rtData.setMode("auto");
                            rtData.setErrorCode(rtData.getErrorCode() & 0b111111101111111111111111);
                            String autoNextStart = "Re-evaluated next start time to " + rtData.getNextStartTime();
                            return new JsonResponse(JsonResponse.Status.OK, autoNextStart);
                        case "m":
                            rtData.setMode("manual");
                            rtData.setErrorCode(rtData.getErrorCode() | 0b000000010000000000000000);
                            return new JsonResponse(JsonResponse.Status.OK, "Switched to manual mode");
                        default:
                            return new JsonResponse(JsonResponse.Status.NOK, "Invalid mode. Use 'a' for auto or 'm' for manual");
                    }

                case "skip":
                    if (command.getParameters() == null || command.getParameters().length == 0) {
                        return new JsonResponse(JsonResponse.Status.NOK, "Mode parameter required (a/m)");
                    }
                    String skipWhat = command.getParameters()[0].toString();
                    switch (skipWhat) {
                        case "z":
                            rtData.setSkipZoneFlag(true);
                            rtData.setErrorCode(rtData.getErrorCode() & 0b111111101111111111111111);
                            return new JsonResponse(JsonResponse.Status.OK, "Skip current zone");
                        case "c":
                            rtData.setSkipCycleFlag(true);
                            rtData.setErrorCode(rtData.getErrorCode() | 0b000000010000000000000000);
                            return new JsonResponse(JsonResponse.Status.OK, "Skip current cycle");
                        default:
                            return new JsonResponse(JsonResponse.Status.NOK, "Invalid mode. Use 'z' for zone or 'c' for cycle");
                    }

                case "start":
                case "stop":
                    if (command.getParameters() == null || command.getParameters().length == 0) {
                        return new JsonResponse(JsonResponse.Status.NOK, "Zone parameter required");
                    }
                    try {
                        int zone = Integer.parseInt(command.getParameters()[0].toString());
                        if (zone >= 0 && zone < parms.getZones()) {
                            rtData.setValveStatus(zone, command.getCommand().equals("start"));
                            return new JsonResponse(JsonResponse.Status.OK, null);
                        } else {
                            return new JsonResponse(JsonResponse.Status.NOK, 
                                "Invalid zone. Valid ids are from 0 to " + (parms.getZones() - 1));
                        }
                    } catch (NumberFormatException e) {
                        return new JsonResponse(JsonResponse.Status.NOK, 
                            "Malformatted zone parameter: " + command.getParameters()[0]);
                    }

                default:
                    return new JsonResponse(JsonResponse.Status.NOK, "Unknown command");
            }
        } catch (Exception e) {
            logger.error("Error executing command: " + e.getMessage(), e);
            return new JsonResponse(JsonResponse.Status.NOK, "Error: " + e.getMessage());
        }
    }
} 