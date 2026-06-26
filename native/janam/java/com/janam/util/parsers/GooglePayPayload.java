package com.janam.util.parsers;

public class GooglePayPayload extends NdefTextRecord.NdefTextRecordList
{
	public GooglePayPayload(byte[] spayload)
	{
		super(spayload);
	}

	public NdefTextRecord getFirstNRecord()
	{
		return find("n");
	}

}
