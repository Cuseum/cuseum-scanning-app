package com.janam.util.parsers;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class StringScanner
{

	private String value;
	private int    startIndex = -1;
	private int    byteLength = 0;
	private int    minLength  = 1; // default

	public void setMinLength(int min)
	{
		if (min < 1)
		{
			throw new IllegalArgumentException("minLength must be >= 1");
		}
		this.minLength = min;
	}

	public boolean parse(byte[] data)
	{
		return parse(data, null);
	}
	public boolean parse(byte[] data, Charset charset)
	{
		reset();

		if (data == null || data.length == 0)
			return false;

		if (charset == null)
		{
			charset = StandardCharsets.UTF_8;
		}

		if (isUtf16(charset))
		{
			return parseUtf16(data, charset);
		}
		else
		{
			return parseUtf8AndSingleByte(data, charset);
		}
	}

	// =========================
	// UTF-8 / ASCII / ISO-8859-1
	// =========================
	private boolean parseUtf8AndSingleByte(byte[] data, Charset charset)
	{
		for (int i = 0; i < data.length; i++)
		{
			int index = i;

			try
			{
				while (index < data.length)
				{
					int b = data[index] & 0xFF;

					// NULL terminator (0x00)
					if (b == 0x00)
					{
						break;
					}

					int charLength;

					if (charset.equals(StandardCharsets.UTF_8))
					{
						// UTF-8 validation
						if ((b & 0x80) == 0)
						{
							charLength = 1;
						}
						else if ((b & 0xE0) == 0xC0)
						{
							charLength = 2;
							if (b < 0xC2)
								break;
						}
						else if ((b & 0xF0) == 0xE0)
						{
							charLength = 3;
						}
						else if ((b & 0xF8) == 0xF0)
						{
							charLength = 4;
							if (b > 0xF4)
								break;
						}
						else
						{
							break;
						}

						if (index + charLength > data.length)
							break;

						for (int j = 1; j < charLength; j++)
						{
							int bb = data[index + j] & 0xFF;
							if ((bb & 0xC0) != 0x80)
							{
								throw new IllegalArgumentException("Invalid UTF-8 continuation");
							}
						}

					}
					else
					{
						// Single-byte charset (ASCII / ISO-8859-1)
						charLength = 1;
					}

					index += charLength;
				}

				int length = index - i;

				if (length >= minLength)
				{
					value      = new String(data, i, length, charset);
					startIndex = i;
					byteLength = length;
					return true;
				}

			}
			catch (Exception ignored)
			{
			}
		}

		return false;
	}

	// =========================
	// UTF-16 (all variants)
	// =========================
	private boolean parseUtf16(byte[] data, Charset charset)
	{
		for (int i = 0; i < data.length - 1; i++)
		{

			// enforce 2-byte alignment
			if ((i % 2) != 0)
				continue;

			int index = i;

			while (index + 1 < data.length)
			{

				byte b1 = data[index];
				byte b2 = data[index + 1];

				// NULL terminator = 0x00 0x00
				if (b1 == 0x00 && b2 == 0x00)
				{
					break;
				}

				index += 2;
			}

			int length = index - i;

			if (length >= minLength)
			{
				try
				{
					value      = new String(data, i, length, charset);
					startIndex = i;
					byteLength = length;
					return true;
				}
				catch (Exception ignored)
				{
				}
			}
		}

		return false;
	}

	private boolean isUtf16(Charset charset)
	{
		return charset.equals(StandardCharsets.UTF_16)
				       || charset.equals(StandardCharsets.UTF_16BE)
				       || charset.equals(StandardCharsets.UTF_16LE);
	}

	private void reset()
	{
		value      = null;
		startIndex = -1;
		byteLength = 0;
	}

	public String getString()
	{
		return value;
	}

	public int getStartIndex()
	{
		return startIndex;
	}

	public int getByteLength()
	{
		return byteLength;
	}
}

