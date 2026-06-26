package com.janam.util.parsers;

import android.nfc.NdefMessage;

public class NdefScanner {

	private NdefMessage message;
	private int startIndex = -1;
	private int byteLength = 0;

	public boolean parse(byte[] data) {
		// Reset previous state
		message = null;
		startIndex = -1;
		byteLength = 0;

		if (data == null || data.length == 0) return false;

		for (int i = 0; i < data.length; i++) {
			try {
				int index = i;

				while (index < data.length) {
					int header = data[index] & 0xFF;

					boolean mb = (header & 0x80) != 0;
					boolean me = (header & 0x40) != 0;
					boolean sr = (header & 0x10) != 0;
					boolean il = (header & 0x08) != 0;

					// Must start with MB for a valid message
					if (index == i && !mb) break;

					if (index + 1 >= data.length) break;

					int typeLength = data[index + 1] & 0xFF;
					int offset = 2;

					int payloadLength;
					if (sr) {
						if (index + offset >= data.length) break;
						payloadLength = data[index + offset] & 0xFF;
						offset += 1;
					} else {
						if (index + offset + 3 >= data.length) break;
						payloadLength =
								((data[index + offset] & 0xFF) << 24) |
										((data[index + offset + 1] & 0xFF) << 16) |
										((data[index + offset + 2] & 0xFF) << 8) |
										(data[index + offset + 3] & 0xFF);
						offset += 4;
					}

					int idLength = 0;
					if (il) {
						if (index + offset >= data.length) break;
						idLength = data[index + offset] & 0xFF;
						offset += 1;
					}

					int recordLength = offset + typeLength + idLength + payloadLength;

					if (recordLength <= 0 || index + recordLength > data.length) {
						break;
					}

					index += recordLength;

					if (me) {
						int msgLen = index - i;
						byte[] candidate = new byte[msgLen];
						System.arraycopy(data, i, candidate, 0, msgLen);

						// Try constructing NdefMessage
						message = new NdefMessage(candidate);
						startIndex = i;
						byteLength = msgLen;
						return true;
					}
				}

			} catch (Exception ignored) {
				// Invalid candidate start, continue scanning
			}
		}

		return false;
	}

	public NdefMessage getMessage() {
		return message;
	}

	public int getStartIndex() {
		return startIndex;
	}

	public int getLength() {
		return byteLength;
	}
}