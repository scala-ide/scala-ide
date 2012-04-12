/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package scala.tools.eclipse.debug.spy;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * This class implements the corresponding Java Debug Wire Protocol (JDWP)
 * packet declared by the JDWP specification.
 * 
 */
public abstract class JdwpPacket {
	/** General JDWP constants. */
	public static final byte FLAG_REPLY_PACKET = (byte) 0x80;
	protected static final int MIN_PACKET_LENGTH = 11;

	/** Map with Strings for flag bits. */
	private static String[] fgFlagStrings = null;

	/** Header fields. */
	protected int fId = 0;
	protected byte fFlags = 0;
	protected byte[] fDataBuf = null;

	/**
	 * Set Id.
	 */
	/* package */void setId(int id) {
		fId = id;
	}

	/**
	 * @return Returns Id.
	 */
	public int getId() {
		return fId;
	}

	/**
	 * Set Flags.
	 */
	/* package */void setFlags(byte flags) {
		fFlags = flags;
	}

	/**
	 * @return Returns Flags.
	 */
	public byte getFlags() {
		return fFlags;
	}

	/**
	 * @return Returns total length of packet.
	 */
	public int getLength() {
		return MIN_PACKET_LENGTH + getDataLength();
	}

	/**
	 * @return Returns length of data in packet.
	 */
	public int getDataLength() {
		return fDataBuf == null ? 0 : fDataBuf.length;
	}

	/**
	 * @return Returns data of packet.
	 */
	public byte[] data() {
		return fDataBuf;
	}

	/**
	 * @return Returns DataInputStream with reply data, or an empty stream if
	 *         there is none.
	 */
	public DataInputStream dataInStream() {
		if (fDataBuf != null) {
			return new DataInputStream(new ByteArrayInputStream(fDataBuf));
		}

		return new DataInputStream(new ByteArrayInputStream(new byte[0]));
	}

	/**
	 * Assigns data to packet.
	 */
	public void setData(byte[] data) {
		fDataBuf = data;
	}

	/**
	 * Reads header fields that are specific for a type of packet.
	 */
	protected abstract void readSpecificHeaderFields(
			DataInputStream dataInStream) throws IOException;

	/**
	 * Writes header fields that are specific for a type of packet.
	 */
	protected abstract void writeSpecificHeaderFields(
			DataOutputStream dataOutStream) throws IOException;

	/**
	 * Reads complete packet.
	 */
	public static JdwpPacket read(InputStream inStream) throws IOException {
		DataInputStream dataInStream = new DataInputStream(inStream);

		// Read header.
		int packetLength = dataInStream.readInt();
		int id = dataInStream.readInt();
		byte flags = dataInStream.readByte();

		// Determine type: command or reply.
		JdwpPacket packet;
		if ((flags & FLAG_REPLY_PACKET) != 0)
			packet = new JdwpReplyPacket();
		else
			packet = new JdwpCommandPacket();

		// Assign generic header fields.
		packet.setId(id);
		packet.setFlags(flags);

		// Read specific header fields and data.
		packet.readSpecificHeaderFields(dataInStream);
		if (packetLength - MIN_PACKET_LENGTH > 0) {
			packet.fDataBuf = new byte[packetLength - MIN_PACKET_LENGTH];
			dataInStream.readFully(packet.fDataBuf);
		}

		return packet;
	}

	/**
	 * Writes complete packet.
	 */
	public void write(OutputStream outStream) throws IOException {
		DataOutputStream dataOutStream = new DataOutputStream(outStream);

		writeHeader(dataOutStream);
		writeData(dataOutStream);
	}

	/**
	 * Writes header of packet.
	 */
	protected void writeHeader(DataOutputStream dataOutStream)
			throws IOException {
		dataOutStream.writeInt(getLength());
		dataOutStream.writeInt(getId());
		dataOutStream.writeByte(getFlags());
		writeSpecificHeaderFields(dataOutStream);
	}

	/**
	 * Writes data of packet.
	 */
	protected void writeData(DataOutputStream dataOutStream) throws IOException {
		if (fDataBuf != null) {
			dataOutStream.write(fDataBuf);
		}
	}

	/**
	 * Retrieves constant mappings.
	 */
	public static void getConstantMaps() {
		if (fgFlagStrings != null) {
			return;
		}

		Field[] fields = JdwpPacket.class.getDeclaredFields();
		fgFlagStrings = new String[8];

		for (Field field : fields) {
			if ((field.getModifiers() & Modifier.PUBLIC) == 0
					|| (field.getModifiers() & Modifier.STATIC) == 0
					|| (field.getModifiers() & Modifier.FINAL) == 0) {
				continue;
			}

			String name = field.getName();
			if (!name.startsWith("FLAG_")) {//$NON-NLS-1$
				continue;
			}

			name = name.substring(5);

			try {
				byte value = field.getByte(null);

				for (int j = 0; j < fgFlagStrings.length; j++) {
					if ((1 << j & value) != 0) {
						fgFlagStrings[j] = name;
						break;
					}
				}
			} catch (IllegalAccessException e) {
				// Will not occur for own class.
			} catch (IllegalArgumentException e) {
				// Should not occur.
				// We should take care that all public static final constants
				// in this class are bytes.
			}
		}
	}

	/**
	 * @return Returns a mapping with string representations of flags.
	 */
	public static String[] getFlagMap() {
		getConstantMaps();
		return fgFlagStrings;
	}
}
