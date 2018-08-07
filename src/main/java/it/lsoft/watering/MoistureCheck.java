package it.lsoft.watering;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;

import it.lsoft.watering.Commons.Parameters;
import it.lsoft.watering.Raspberry.RealTimeData;

public class MoistureCheck extends Thread
{
	private class MoistureCheckEntry {
		private boolean checked;
		private Date timeToCheck;
		public MoistureCheckEntry()
		{
			checked = false;
		}
	}
	
	private RealTimeData rtData;
	private Date now;
	private MoistureCheckEntry[] checkList = null;
	private Parameters parms = null;

	static Logger logger = Logger.getLogger(MoistureCheck.class);

	public MoistureCheck(RealTimeData rtData)
	{
		this.rtData = rtData;
	}
	
	@Override
	public void run() 
	{
		logger.debug("Moisture check thread started");
		SimpleDateFormat longFmt = rtData.getLongFmt();
		parms = rtData.getParms();
		
		while(!rtData.isShutDown())
		{
			now = new Date();
			// Initiate the watering effectiveness check only if: the set-time from watering is over, 
			// it wasn't initiated already (checkList = null) and it wasn't requested a skip for this cycle
			if ((longFmt.format(now).compareTo(rtData.getNextStartTime()) == 0) && (checkList == null))
			{
				logger.info("watering effectiveness time is arrived. Set the checktime array accordingly");
				if  (rtData.isSkipFlag() || rtData.isDisableFlag() || rtData.isSuspendFlag())
				{
					logger.info("...but unfortunately the watering request is somehow cancelled (Skip-Dis-Susp: " +
								rtData.isSkipFlag() + " - " + rtData.isDisableFlag() + " - " + rtData.isSuspendFlag() + ")." );
					logger.info("Going back to rest");
				}
				else
				{
					checkList = new MoistureCheckEntry[parms.getZones()];
					checkList[0] = new MoistureCheckEntry();
					checkList[0].timeToCheck = new Date(
											now.getTime() + 
											parms.getZoneDuration(0, rtData, Watering.dayOfWeekAsNumber(now)) * 1000 +
											parms.getMinutesToEvalWateringEffectivess() * 1000 * 60
										);
					for(int i = 1; i < parms.getZones(); i++)
					{
						checkList[i] = new MoistureCheckEntry();
						checkList[i].timeToCheck = new Date(
											checkList[i - 1].timeToCheck.getTime() + 
											parms.getZoneDuration(i, rtData, Watering.dayOfWeekAsNumber(now)) * 1000
										);	   
					}
				}
			}
			evalMoistureEffectiveness();

			evalSkipNextCycle();
			try 
			{
				Thread.sleep(1000);
			} 
			catch (InterruptedException e) 
			{
				;
			}

		}
	}
	
	private void evalMoistureEffectiveness()
	{
		int i;
		
		if (checkList != null)
		{
			for(i = parms.getZones() - 1; i >= 0 && !checkList[i].checked; i--)
			{
				if (now.getTime() > checkList[i].timeToCheck.getTime())
				{
					checkList[i].checked = true;
					int sensorId = parms.getSensorIdPerArea()[i];
					if (sensorId != -1) 
					{
						logger.debug("Check watering effectiveness on the area " + sensorId);
						if ((rtData.getMoisture(sensorId) != null) &&
							(rtData.getMoisture(sensorId) < parms.getExpectedMoistureAfterWatering()[sensorId]))
						{
							String mailBody = "Moisture on sensor " + sensorId + 
											  " did not reach the expected level  during last watering session(" +
											  rtData.getMoisture(sensorId) + " - " + 
											  parms.getExpectedMoistureAfterWatering()[sensorId] + ").";
							logger.error(mailBody);
							logger.error("Reset the skip and autoSkip flag");
							rtData.setSkipFlag(false);
							parms.setEnableAutoSkip(false);
							sendAlertByMail(mailBody);
						}
						else
						{
							logger.error("Moisture on sensor " + sensorId + " is at the expected level (" +
									rtData.getMoisture(sensorId) + " - " + parms.getExpectedMoistureAfterWatering()[sensorId] + ").");
						}
					}
					break;
				}
			}
			if (i == parms.getZones() - 1)
			{
				checkList = null;
				logger.error("Watering effectiveness check completed");
			}
		}
	}

	private void evalSkipNextCycle()
	{	
		// Check only if we are before the next cycle (so not in a watering cycle) and no skipFlag has been raised yet
		if ((now.getTime() < rtData.getNextStartTimeAsDate().getTime() - parms.getMinutesToSkipFlagCheck() * 60000) || 
			(rtData.getInCycle() >= 0) ||
			rtData.isSkipFlag())
		{
			return;
		}
		
		/*
		 * Check if it is time to archive the moisture read by sensors and do it
		 */
		for(int i = 0; i < parms.getNumberOfSensors(); i++)
		{
			if (rtData.getMoisture(i) != null)
			{
				if (rtData.getMoisture(i) > parms.getSkipTreshold()[i])
				{
					String mailBody = "Moisture on sensor " + i + " is bigger than the related treshold (" +
									  rtData.getMoisture(i) + " - " + parms.getSkipTreshold()[i] + "). Setting the skipFlag to " + 
									  rtData.getParms().isEnableAutoSkip();
					logger.debug(mailBody);
					rtData.setSkipFlag(rtData.getParms().isEnableAutoSkip());
					sendAlertByMail(mailBody);
				}
			}
		}
	}

	public void sendAlertByMail(String mailBody)
	{
		// Sender's email ID needs to be mentioned
		Properties props = new Properties();

		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", parms.getMailHost());
		props.put("mail.smtp.port", "25");
		//		props.put("mail.smtp.port", "587");
		props.put("mail.smtp.user", parms.getMailUsername());
		props.put("mail.smtp.password", parms.getMailPassword());
		//		props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
		if (parms.istMailUseSSL())
		{
			props.put("mail.smtp.ssl.trust", parms.getMailSmtpSSLTrust());
		}

		// Get the Session object.
		Session session = Session.getInstance(props,
				new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(parms.getMailUsername(), parms.getMailPassword());
			}
		});

		try {
			// Create a default MimeMessage object.
			Message message = new MimeMessage(session);

			// Set From: header field of the header.
			message.setFrom(new InternetAddress(parms.getMailFrom()));

			// Set To: header field of the header.
			message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(parms.getMailTo()));

			// Set Subject: header field
			message.setSubject("Watering alert - new alert raised by the system");

			// Create a multipar message and the message part
			Multipart multipart = new MimeMultipart();
			BodyPart messageBodyPart = new MimeBodyPart();
			DataSource source;
			try
			{
				// Now set the actual message
				messageBodyPart.setContent(mailBody.toString(),"text/html");
				multipart.addBodyPart(messageBodyPart);
				message.setContent(multipart);

				logger.info("Sending new alert to " + parms.getMailFrom());
				Transport.send(message);
				logger.info("Successfully sent....");
			}
			catch(Exception e)
			{
				logger.error("Expection " + e.getMessage() + " sending new alert");
			}
		} 
		catch (MessagingException e) 
		{
			throw new RuntimeException(e);
		}
	}
}
