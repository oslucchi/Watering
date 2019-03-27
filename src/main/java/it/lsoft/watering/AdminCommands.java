package it.lsoft.watering;

import it.lsoft.watering.Commons.Errors;
import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.Raspberry.RealTimeData;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

public class AdminCommands extends Thread 
{
	private ServerSocket serverSocket = null;
	private Parameters parms;
	private BufferedReader br;
	private BufferedWriter wr;
	private String input = "";
	private RealTimeData rtData;

	private final Logger logger = Logger.getLogger(AdminCommands.class);
	
	public AdminCommands(RealTimeData rtData)
	{
		this.parms = rtData.getParms();
		if (parms.isAdminViaSocket())
		{
			try 
			{
				serverSocket = new ServerSocket(parms.getAdminPort());
			}
			catch (IOException e) 
			{
				logger.fatal("Exception " + e.getMessage() + " on ServerSocket cretion");
				rtData.setShutDown(true);
			}
		}
		this.rtData = rtData;
	}

	public void run() 
	{
        Socket clientSocket = null;
		br = new BufferedReader(new InputStreamReader(System.in));
		wr = new BufferedWriter(new OutputStreamWriter(System.out));
		while(!rtData.isShutDown())
		{
			parms = rtData.getParms();
			
			if (parms.isAdminViaSocket())
			{
				try 
				{
	                clientSocket = serverSocket.accept();
					logger.trace("Connected client " + clientSocket.getInetAddress().getHostName());
					br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
					wr = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
				}
				catch (IOException e) 
				{
					logger.fatal("Exception " + e.getMessage() + " on ServerSocket cretion");
					rtData.setShutDown(true);
					break;
				}
			}
			try
			{
				wr.write("\nWatering admin> ");
				wr.flush();
				boolean clientWantsToExit = false;
				while((parms.isAdminViaSocket() && !clientWantsToExit) ||
					  (!parms.isAdminViaSocket() && !rtData.isShutDown()))
				{
					if (br.ready())
					{
						boolean isStartCmd = false;
						try
						{
							input = br.readLine();
						}
						catch(SocketTimeoutException e)
						{
							logger.error("the user has been to quiet. Dropping the connection");
							wr.write("Too long to decide what you wonna do with me... try to reconnect\n");
							clientWantsToExit = true;
							break;
						}
						logger.trace("input length " + input.length());
						if (input.length() == 0)
						{
							wr.write("NACK\n");
							wr.write("\nWatering admin> ");
							wr.flush();
							continue;
						}
						for(char c : input.toCharArray())
						{
							if ((((int)c) < 32) || (((int)c) > 127))
							{
								logger.trace("bad input");
								input = "quit";
								break;
							}
						}
						StringTokenizer st = new StringTokenizer(input);
						
						String command = null;
						command = st.nextToken().toLowerCase();
						logger.trace("handling command '" + command + "'");
						switch(command)
						{
						case "disable":
							rtData.setDisableFlag(true);
							wr.write("ACK\n");
							break;
							
						case "enable":
							rtData.setDisableFlag(false);
							wr.write("ACK\n");
							wr.write("Re-evaluated next start time to " + rtData.getNextStartTime());
							break;
							
						case "suspend":
							rtData.setSuspendFlag(true);
							wr.write("Ssuspend flag is now: " + rtData.isSuspendFlag() + "\n");
							wr.write("ACK\n");
							break;

						case "resume":
							rtData.setSuspendFlag(false);
							wr.write("Suspend flag is now: " + rtData.isSuspendFlag() + "\n");
							wr.write("ACK\n");
							break;
							
						case "skip":
							rtData.setSkipFlag(true);
							wr.write("Skip flag is now: " + rtData.isSkipFlag() + "\n");
							wr.write("ACK\n");
							break;
							
						case "reset":
							rtData.setSkipFlag(false);
							wr.write("Skip flag is now: " + rtData.isSkipFlag() + "\n");
							wr.write("ACK\n");
							break;
							
						case "startman":
							if (st.hasMoreTokens())
							{
								String inputParm = st.nextToken();
								try
								{
									int schedule = Integer.parseInt(inputParm);
									if ((schedule < 0) || (schedule > parms.getNumberOfSchedules()))
									{
										wr.write("NACK\n");
										wr.write("Invalid schedule. Value permitted from 0 to " + 
												  parms.getNumberOfSchedules() + "\n");
									}
									else
									{
										rtData.setForceManual(true);
										rtData.setScheduleIndex(schedule);
										wr.write("ACK\n");
									}
								}
								catch(NumberFormatException e)
								{
									wr.write("NACK\n");
									wr.write("malformatted delay '" + inputParm + "\n");
								}
							}

							break;
							
						case "start":
							isStartCmd = true;
						case "stop":
							if (st.hasMoreTokens())
							{
								String inputParm = st.nextToken();
								try
								{
									int zone = Integer.parseInt(inputParm);
									if ((zone >= 0) && (parms.getZones() > zone))
									{
										rtData.setValveStatus(zone, isStartCmd);
										wr.write("ACK\n");
									}
									else
									{
										wr.write("NACK\n");
										wr.write("invalid zone. Valid ids are from 0 to " + 
												 (parms.getZones() - 1) + "\n");

									}
								}
								catch(NumberFormatException e)
								{
									wr.write("NACK\n");
									wr.write("malformatted zone'" + inputParm + "\n");
								}
							}
							break;
							
						case "status":
							wr.write("ACK-M\n");
							if (rtData.getMode().compareTo("manual") == 0)
							{
								wr.write("\tThe system is running in manual mode\n");
							}
							else
							{
								wr.write("\tNext startup at: " + rtData.getNextStartTime() + "\n");
							}
							wr.write("\tHumidity from sensors:\n");
							for(int i = 0; i < parms.getNumberOfSensors(); i++)
							{
								wr.write("\t\tSensor " + i + " " + String.format("%2.2f", rtData.getMoisture(i)) + "\n");
							}
							wr.write("\tWatering is " + 
									(rtData.getInCycle() < 0 ? "inactive\n" : "active on zone " + rtData.getInCycle() + ":\n"));
							if (rtData.getInCycle() >= 0 )
							{
								for(int i = 0; i < parms.getZones(); i++)
								{
									wr.write("\t\tZone " + i + " " + 
											 (rtData.getValveStatus(i) ? "watering" : "   off  ") + 
											 (rtData.getValveStatus(i) ? " since " + rtData.getWateringTimeElapsed(i) + " sec" : "") +  "\n");
								}
							}
							wr.write("\tDisable flag is   : " + rtData.isDisableFlag() + "\n");
							wr.write("\tSuspend flag is   : " + rtData.isSuspendFlag() + "\n");
							wr.write("\tSkip flag is      : " + rtData.isSkipFlag() + "\n");
							wr.write("\tForce flag is     : " + rtData.isForceManual() + "\n");
							wr.write("\tAutoSkip flag is  : " + parms.isEnableAutoSkip() + "\n");
							wr.write("\tError code is     : " + rtData.getErrorCode() + "\n");
							wr.write("\tSensor dumping is : " + rtData.getParms().isDumpSensorReading() + "\n");
							if (rtData.getDelayByMinutes() > 0)
								wr.write("\tStart delayed by: " + rtData.getDelayByMinutes() + " min\n");
							wr.write("ACK-ENDM\n");
							break;

						case "seterr":
							if (st.hasMoreTokens())
							{
								String inputParm = st.nextToken();
								try
								{
									int errorCode = Integer.parseInt(inputParm);
									rtData.setErrorCode(errorCode);
									wr.write("ACK\n");
								}
								catch(NumberFormatException e)
								{
									wr.write("NACK\n");
									wr.write("malformatted error code '" + inputParm + "\n");
								}
							}
							break;

						case "mode":
							if (st.hasMoreTokens())
							{
								String inputParm = st.nextToken();
								try
								{
									switch(inputParm)
									{
									case "a":
										rtData.setMode("auto");
										rtData.setErrorCode(rtData.getErrorCode() & 0b111111101111111111111111);
										wr.write("Re-evaluated next start time to " + rtData.getNextStartTime());
										wr.write("ACK\n");
										break;
									case "m":
										rtData.setMode("manual");
										rtData.setErrorCode(rtData.getErrorCode() | Errors.STATUS_MANUAL);
										break;
									default:
										wr.write("NACK\n");
										wr.write("bad status '" + inputParm + "'\n");
									}
									wr.write("ACK\n");
								}
								catch(NumberFormatException e)
								{
									wr.write("NACK\n");
									wr.write("malformatted error code '" + inputParm + "\n");
								}
							}
							break;

						case "delay":
							if (st.hasMoreTokens())
							{
								String inputParm = st.nextToken();
								try
								{
									int delay = Integer.parseInt(inputParm);
									if ((delay < 0) || (delay >= 120))
									{
										wr.write("NACK\n");
										wr.write("Invalid delay. Value permitted from 0 to 120\n");
									}
									else
									{
										rtData.setRequiredChangeOnStartTime(true);
										rtData.setDelayByMinutes(delay);
										wr.write("ACK\n");
									}
								}
								catch(NumberFormatException e)
								{
									wr.write("NACK\n");
									wr.write("malformatted delay '" + inputParm + "\n");
								}
							}
							break;
						
						case "shutdown":
							wr.write("ACK-M\n");
							wr.write("\n\tShutting down .....\n");
							wr.write("ACK-ENDM\n");
							wr.flush();
							clientSocket.close();
							rtData.setShutDown(true);
							logger.debug("Shutting the system down");
							return;

						case "rescan":
							rtData.setParms(parms.rescan());
							rtData.evalFirstStart();
							wr.write("Next startup at: " + rtData.getNextStartTime() + "\n");
							wr.write("ACK\n");
							break;

						case "autoskip":
							parms.setEnableAutoSkip(!parms.isEnableAutoSkip());
							wr.write("AutoSkip flag is now: " + parms.isEnableAutoSkip() + "\n");
							wr.write("ACK\n");
							break;
							
						case "readval":
							parms.setDumpSensorReading(!parms.isDumpSensorReading());
							wr.write("Sensor dumping is: " + parms.isDumpSensorReading() + "\n");
							wr.write("ACK\n");
							break;

						case "testauto":
							if (st.hasMoreTokens())
							{
								String inputParm = "";
								String sep = "";
								while (st.hasMoreTokens())
								{
									inputParm += sep + st.nextToken();
									sep = " ";
								}
								try
								{
									SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
									rtData.setLastWateringSession(formatter.parse(inputParm));
									wr.write("last watering session time set to: " + rtData.getLastWateringSession() + "\n");
									wr.write("ACK\n");
								}
								catch (ParseException e) 
								{
									wr.write("NACK\n");
									wr.write("malformed date specified '" + inputParm + "\n");
								} 
							}
							else
							{
								wr.write("last watering session time set is: " + rtData.getLastWateringSession() + "\n");
								wr.write("ACK\n");								
							}
							break;

						case "help":
							wr.write("ACK-M\n");
							wr.write("Available commands:\n");
							wr.write("\tdisable     force disable watering\n");
							wr.write("\tensable     reset watering to normal logic\n");
							wr.write("\tsuspend     suspend current zone watering\n");
							wr.write("\tresume      resume previously suspended watering\n");
							wr.write("\tskip        skip the next watering cycle\n");
							wr.write("\treset       reset a skip cycle flag previously set\n");
							wr.write("\tstartman x  forces a manual start of a cycle on the x-th schedule\n");
							wr.write("\tstart n     force start watering of the nth zone\n");
							wr.write("\tstop n      stopt forcing the nth zone\n");
							wr.write("\tstatus      print current watering system status\n");
							wr.write("\tseterr x    sets the error code to the value x\n");
							wr.write("\tdelay n     delay next start by n minutes\n");
							wr.write("\tshutdown    shusts down the watering system\n");
							wr.write("\trescan      rescan params file and use updated values\n");
							wr.write("\tautoskip    toggle auto skip based on the moisture value\n");
							wr.write("\treadval     toggle dumping sensors value each reading\n");
							wr.write("\tmode a|m set watring in manual/auto mode\n");
							wr.write("\tquit        exits this shell\n");
							wr.write("ACK-ENDM\n");
							break;
							
						case "quit":
							clientWantsToExit = true;
							break;
							
						default:
							wr.write("NACK\n");
							wr.write("Unknown command '" + command + "\n");
						}
						if(!rtData.isShutDown())
						{
							wr.write("\nWatering admin> ");
						}
						wr.flush();
					}
				}
				clientSocket.close();
			}
			catch (IOException e) 
			{
				logger.fatal("Watering console could not be started. Shutting down");
				rtData.setShutDown(true);
			}
		}
	}
}
