package it.lsoft.watering.Commons;

import java.io.PrintWriter;
import java.io.StringWriter;

public class Utility 
{
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
}
