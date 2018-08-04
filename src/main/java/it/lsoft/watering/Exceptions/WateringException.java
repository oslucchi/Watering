package it.lsoft.watering.Exceptions;

public class WateringException extends Exception 
{
	private static final long serialVersionUID = -8166519137963232242L;
	public static final int ERR_NO_RECORD_FOUND = 1;

	private String errorDescription;
	private int errorCode = 0;

	public WateringException() 
	{
		super();
	}

	public WateringException(String msg) 
	{
		super(msg);
	}
	
	public WateringException(Exception e)
	{
		super("Exception: class '" + 
			  e.getStackTrace().getClass().getSimpleName() + "' error msg: '" + e.getMessage());
	}
	
	public WateringException(String errorDescription, int errorCode) 
	{
		super("internal error: (" + errorCode +  ") " + errorDescription);
		this.errorDescription = errorDescription;
		this.errorCode = errorCode;
	}

	public WateringException(Throwable t)
	{
		super("Tracker exception: class '" + 
			  t.getStackTrace().getClass().getSimpleName() + "' error msg: '" + t.getMessage());
	}

	public String getErrorDescription() {
		return errorDescription;
	}

	public void setErrorDescription(String errorDescription) {
		this.errorDescription = errorDescription;
	}

	public int getErrorCode() {
		return errorCode;
	}

	public void setErrorCode(int errorCode) {
		this.errorCode = errorCode;
	}
	
}
