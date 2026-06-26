package com.janam.util;


import android.annotation.SuppressLint;

public class BinUtils
{
	private static final char[] hex = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	private static String hexdumpAddressFormat = "%06X ";
	public static final class Options
	{
		public int hexdumpIndent = 0;
		public int hexdumpLimit = 256;
		public int hexdumpAddressDigits = 6;
	}
	public static Options Options = new Options();

	public static boolean isPrint(byte b)
	{
		if(b <= 0x1F || b >= 0x7F)
			return false;
		return true;
	}

	private static void putblock(StringBuilder sb, int addr, byte[] data, int offset, int count)
	{
		int i;
		int j;

		if(Options.hexdumpIndent > 0)
			for(i=0;i<Options.hexdumpIndent;i++)
			    sb.append(' ');

		sb.append(String.format(hexdumpAddressFormat,addr));
		for (j = count, i = 0; i < 16; i++, j--)
		{
			if (j > 0)
			{
				byte b = data[offset+i];
				sb.append(hex[(b >> 4)&0xF]);
				sb.append(hex[b & 0xF]);
				sb.append(' ');
			}
			else
				sb.append("   ");
			if(i == 7)
				sb.append(' ');

		}
		sb.append(" ");
		for (j = count, i = 0; i < 16; i++, j--)
		{
			if(j > 0)
			{
				byte b = data[offset+i];
				if(!isPrint(b))
					sb.append('.');
				else
					sb.append((char)b);
				if(i == 7)
					sb.append(" ");
			}
			else
				sb.append(' ');
		}
		sb.append('\n');
	}

	public static StringBuilder hexdump(byte[] data)
	{
		return hexdump(data,data.length);
	}

	public static StringBuilder hexdump(byte[] data, int length)
	{
		if(Options.hexdumpAddressDigits > 0)
			hexdumpAddressFormat = String.format("%%0%dX ",Options.hexdumpAddressDigits);
		StringBuilder sb = new StringBuilder();
		int i = 0;
		int offset = 0;
		for (i = 0; length > 15; length -= 16, offset += 16, i += 16)
		     putblock (sb, i, data, offset, 16);
		if(length > 0)
			putblock (sb, i, data, offset, length);
		sb.append("\n");
		return sb;
	}

	@SuppressLint("DefaultLocale")
	private static String formatLength(int length)
	{
		return String.format("(%d):",length);
	}

	@SuppressLint("DefaultLocale")
	public static String toFormattedHexString(boolean showLength, byte[] bytes, int pos, int length)
	{
		// stupidity filter 1
		if(bytes == null || pos >= bytes.length || pos < 0 || length <= 0)
			return showLength?formatLength(0):"";

		int dataLength = length;

		// stupidity filter 2
		if((pos+length) > bytes.length)
			dataLength = bytes.length - pos;

		StringBuilder sb = new StringBuilder(dataLength * 2);

		if(showLength)
			sb.append(formatLength(dataLength));

		for(int i = 0; i < dataLength; i++)
		{
			int b = bytes[pos+i] & 0xFF;
			if(i > 0)
			{
				sb.append(' ');
				if(i % 4 == 0)
					sb.append(' ');
			}
			sb.append(hex[b >> 4]);
			sb.append(hex[b & 0xF]);
		}

		return sb.toString();
	}
	public static String toFormattedHexString(byte[] bytes, int pos, int length)
	{
		return toFormattedHexString(false,bytes, pos, length);
	}

	public static String toFormattedHexString(boolean showLength,byte[] bytes)
	{
		if(bytes == null || bytes.length == 0)
			return showLength?formatLength(0):"";
		return toFormattedHexString(showLength, bytes,0,bytes.length);
	}
	public static String toFormattedHexString(byte[] bytes)
	{
		return toFormattedHexString(false,bytes);
	}

	@SuppressLint("DefaultLocale")
	public static String toHexString(boolean showLength, byte[] bytes, int pos, int length)
	{
		// stupidity filter 1
		if(bytes == null || pos >= bytes.length || pos < 0 || length <= 0)
			return showLength?formatLength(0):"";

		int dataLength = length;

		// stupidity filter 2
		if((pos+length) > bytes.length)
			dataLength = bytes.length - pos;

		StringBuilder sb = new StringBuilder(dataLength * 2);

		if(showLength)
			sb.append(formatLength(dataLength));

		for(int i = 0; i < dataLength; i++)
		{
			int b = bytes[pos+i] & 0xFF;
			sb.append(hex[b >> 4]);
			sb.append(hex[b & 0xF]);
		}

		return sb.toString();
	}

	public static String toHexString(byte[] bytes, int pos, int length)
	{
		return toHexString(false,bytes,pos,length);
	}

	public static String toHexString(boolean showLength,byte[] bytes)
	{
		if(bytes == null || bytes.length == 0)
			return showLength?formatLength(0):"";
		return toHexString(showLength, bytes,0,bytes.length);

	}
	public static String toHexString(byte[] bytes)
	{
		return toHexString(false,bytes);
	}

	public static String toCSVHexString(byte[] bytes, int pos, int length)
	{
		// stupidity filter 1
		if(bytes == null || pos >= bytes.length || pos < 0 || length <= 0)
			return "";

		int dataLength = length;

		// stupidity filter 2
		if((pos+length) > bytes.length)
			dataLength = bytes.length - pos;

		StringBuilder sb = new StringBuilder(dataLength * 2);
		for(int i = 0; i < dataLength; i++)
		{
			int b = bytes[pos+i] & 0xFF;
			sb.append(hex[b >> 4]);
			sb.append(hex[b & 0xF]);
			if(i < (dataLength - 1))
			{
				sb.append(", ");
			}
		}

		return sb.toString();
	}

	public static String toCSVHexString(byte[] bytes)
	{
		if(bytes == null || bytes.length == 0)
			return "";
		return toCSVHexString(bytes,0,bytes.length);
	}

	public static byte[] fromCSVHexString(String s)
	{
		String[] hexValues = s.split(",");
		if(hexValues.length > 0)
		{
			byte byteArray[] = new byte[hexValues.length];
			for(int i = 0; i < hexValues.length; i++)
			{
				hexValues[i] = hexValues[i].trim();
				byteArray[i] = (byte) Integer.parseInt(hexValues[i], 16);
			}
			return byteArray;
		}
		return null;
	}


	public static byte[] parseHex(String hexString)
	{
		return toByteArray(hexString);
	}
	public static byte[] toByteArray(String hexString)
	{
		final char[] s = hexString.replace("\n", "").replace(" ", "").toUpperCase().toCharArray();
		final int len = s.length;
		// "111" is not a valid hex encoding.
		if(len % 2 != 0)
		{
			throw new IllegalArgumentException("hexBinary needs to be even-length: " + s);
		}

		byte[] out = new byte[len / 2];

		for(int i = 0; i < len; i += 2)
		{
			int h = hexToBin(s[i]);
			int l = hexToBin(s[i + 1]);
			if(h == -1 || l == -1)
			{
				throw new IllegalArgumentException("contains illegal character for hexBinary: " + s);
			}

			out[i / 2] = (byte) (h * 16 + l);
		}

		return out;
	}

	public static byte[] toByteArray(int value, int size) // big endian
	{
		byte[] bytes = new byte[size];
		for(int i=0;i<size;i++)
		{
			bytes[size-1-i] = (byte)(value & 0xFF);
			value >>= 8;
		}
		return bytes;
	}

	public static byte[] toByteArray(short value, int size) // big endian
	{
		int intValue = value;
		intValue &= 0xFFFF;
		return toByteArray(intValue,size);
	}
	public static byte[] toByteArray(byte value, int size) // big endian
	{
		int intValue = value;
		intValue &= 0xFF;
		return toByteArray(intValue,size);
	}

	private static int hexToBin(char ch)
	{
		switch(ch)
		{
		case '0': case '1': case '2': case '3': case '4':
		case '5': case '6': case '7': case '8': case '9':
			return ch - '0';
		case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
			return ch - 'A' + 10;
		case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
			return ch - 'a' + 10;
		}

		return -1;
	}

	public static byte[] concatenate(byte[]... arrays)
	{
		int length = 0;
		for (byte[] array : arrays)
		{
			length += array.length;
		}
		byte[] result = new byte[length];
		int    offset = 0;
		for (byte[] array : arrays)
		{
			System.arraycopy(array, 0, result, offset, array.length);
			offset += array.length;
		}
		return result;
	}

	public static byte[] slice(byte[] src, int start, int end)
	{
		byte[] out = new byte[end - start];
		System.arraycopy(src, start, out, 0, out.length);
		return out;
	}
}
