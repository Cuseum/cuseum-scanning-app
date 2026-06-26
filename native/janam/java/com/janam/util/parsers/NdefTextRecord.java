package com.janam.util.parsers;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;

import com.janam.log.LogHelper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class NdefTextRecord
{
	private static String TAG = "NdefTextRecord";

	private       String text = "";
	private       String lang = "";
	private       String path = "";
	private final String recordId;

	public String getText()
	{
		return text;
	}

	public String getLang()
	{
		return lang;
	}

	public String getPath()
	{
		return path;
	}

	public String getRecordId()
	{
		return recordId;
	}

	public NdefTextRecord(NdefRecord ndefRecord)
	{
		byte[] id = ndefRecord.getId();
		recordId = new String(id, StandardCharsets.UTF_8);
		byte[]  payload = ndefRecord.getPayload();
		byte[]  buf;
		Charset charset;
		if ((payload[0] & 0x80) != 0)
		{
			charset = StandardCharsets.UTF_16BE;
		}
		else
		{
			charset = StandardCharsets.UTF_8;
		}
		int langLen = (int) (payload[0] & 0x3F);
		if (langLen > 0)
		{
			buf = new byte[langLen];
			System.arraycopy(payload, 1, buf, 0, langLen);
			lang = new String(buf, StandardCharsets.US_ASCII);
		}
		int textLen = payload.length - (1 + langLen);
		if (textLen > 0)
		{
			buf = new byte[textLen];
			System.arraycopy(payload, 1 + langLen, buf, 0, textLen);
			text = new String(buf, charset);
		}
	}

	public String toString()
	{
		StringBuilder sb = new StringBuilder();
		sb.append("{text:\"" + text + "\"");
		if (lang.length() > 0)
		{
			sb.append(" lang:\"" + lang + "\"");
		}
		sb.append("}");
		return sb.toString();
	}

	public static NdefTextRecordList getList(byte[] spayload)
	{
		try
		{
			return new NdefTextRecordList(new NdefMessage(spayload));
		}
		catch (Exception e)
		{
			LogHelper.e(TAG, e.getMessage());
		}
		return null;
	}

	public static NdefTextRecordList getList(NdefMessage ndefMessage)
	{
		try
		{
			return new NdefTextRecordList(ndefMessage);
		}
		catch (Exception e)
		{
			LogHelper.e(TAG, e.getMessage());
//			e.printStackTrace();
		}
		return null;
	}

	public static class NdefTextRecordList extends ArrayList<NdefTextRecord>
	{
		public NdefTextRecord find(String recordId)
		{
			for (NdefTextRecord tr : this)
			{
				if (tr.getRecordId().equalsIgnoreCase(recordId))
				{
					return tr;
				}
			}
			return null;
		}

		private NdefTextRecordList()
		{

		}

		private static NdefMessage newNdefMessage(byte[] spayload)
		{
			try
			{
				return new NdefMessage(spayload);
			}
			catch (FormatException e)
			{
			}
			return null;
		}

		protected NdefTextRecordList(byte[] spayload)
		{
			this(newNdefMessage(spayload));
		}

		protected NdefTextRecordList(NdefMessage ndefMessage)
		{
			if (ndefMessage == null)
				return;
			ArrayList<String> pathArray = new ArrayList<String>();
			parseNdefTextRecords(ndefMessage, 0, pathArray);
		}

		private void parseNdefTextRecords(NdefMessage message, int level, ArrayList<String> pathArray)
		{
			String indent = "";
			if (level > 0)
			{
				indent = String.format("%1$" + level * 2 + "s", "");
			}

			try
			{
				NdefRecord[] records = message.getRecords();
				if (records != null && records.length > 0)
				{
					for (NdefRecord record : records)
					{
						byte[] t             = NdefRecord.RTD_TEXT;
						String recordType    = new String(record.getType(), StandardCharsets.UTF_8);
						String recordId      = new String(record.getId(), StandardCharsets.UTF_8);
						byte[] recordPayload = record.getPayload();
						int    recordTnf     = record.getTnf();

						LogHelper.d(TAG, "NdefRecord(" + Integer.toString(level) + "): " + indent + "[" + recordType + "]{" + record.toString() + "}");
						switch (recordTnf)
						{
						case NdefRecord.TNF_WELL_KNOWN:
							switch (recordType)
							{
							case "T":
								NdefTextRecord tr = new NdefTextRecord(record);
								LogHelper.d(TAG, "                  " + indent + tr.toString());

								if (recordId != null && recordId.length() > 0)
								{
									if (pathArray.size() < (level + 1))
										pathArray.add(recordId);
									else
										pathArray.set(level, recordId);

								}

								StringBuilder sbPath = new StringBuilder();
								for (int i = 0; i < level; i++)
								     sbPath.append("/" + pathArray.get(i));
								String path = sbPath.toString(); // set the path
								tr.path = path;
								this.add(tr);
								break;
							}
							break;
						case NdefRecord.TNF_EXTERNAL_TYPE:
							if (pathArray.size() < (level + 1))
								pathArray.add(recordType);
							else
								pathArray.set(level, recordType);
							break;
						}

						try
						{
							message = new NdefMessage(record.getPayload());
							parseNdefTextRecords(message, level + 1, pathArray);
						}
						catch (Exception e)
						{
							// not another NdefMessage
						}

					}
				}
			}
			catch (Exception e)
			{
				LogHelper.e(TAG, "Error parsing bytes: ", e);
			}
		}

	}
}
