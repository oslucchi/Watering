package it.lsoft.watering.Commons;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Properties;

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

public class Utility 
{
	static Logger logger = Logger.getLogger(Utility.class);

	public static String printStackTrace(Exception e)
	{
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return (sw.toString()); 
	}
	

	static public String byteToString(byte[] buffer)
	{
		StringBuilder sBuf = new StringBuilder();
		for(int i = 0; i < 6; i++){
			char c = (char) buffer[i];
			sBuf.append(c);
		}
		return sBuf.toString();
	}
	
	static public String byteToBits(byte buf)
	{
		String retVal = "";
		for(int i = 0; i < 8 ; i++)
		{
			if ((buf & (2^i)) != 0)
			{
				retVal += "1";
			}
			else
			{
				retVal += "0";
			}
		}
		return retVal;
	}
	
	static public String stacktraceToString(Exception e)
	{
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString(); 
	}
	

	public static void sendAlertByMail(final Parameters parms, String mailBody)
	{
		// Sender's email ID needs to be mentioned
		Properties props = new Properties();

		props.put("mail.smtp.auth", "true");
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", parms.getMailHost());
//		props.put("mail.smtp.socketFactory.port", parms.getMailPort());
//		props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
		props.put("mail.smtp.port", parms.getMailPort());
		if (parms.istMailUseSSL())
		{
			props.put("mail.smtp.ssl.trust", parms.getMailSmtpSSLTrust());
		}

		logger.info("Pwd: '" + parms.getMailPassword() + "'");
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
				logger.error("Exception " + e.getMessage() + " sending new alert");
			}
		} 
		catch (MessagingException e) 
		{
			throw new RuntimeException(e);
		}
	}

}
