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
package org.scalaide.debug.internal.spy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * This class implements the corresponding Java Debug Wire Protocol (JDWP)
 * packet declared by the JDWP specification.
 * 
 */
public class JdwpReplyPacket extends JdwpPacket {
	/** Error code constants. */
	public static final short NONE = 0;
	public static final short INVALID_THREAD = 10;
	public static final short INVALID_THREAD_GROUP = 11;
	public static final short INVALID_PRIORITY = 12;
	public static final short THREAD_NOT_SUSPENDED = 13;
	public static final short THREAD_SUSPENDED = 14;
	public static final short INVALID_OBJECT = 20;
	public static final short INVALID_CLASS = 21;
	public static final short CLASS_NOT_PREPARED = 22;
	public static final short INVALID_METHODID = 23;
	public static final short INVALID_LOCATION = 24;
	public static final short INVALID_FIELDID = 25;
	public static final short INVALID_FRAMEID = 30;
	public static final short NO_MORE_FRAMES = 31;
	public static final short OPAQUE_FRAME = 32;
	public static final short NOT_CURRENT_FRAME = 33;
	public static final short TYPE_MISMATCH = 34;
	public static final short INVALID_SLOT = 35;
	public static final short DUPLICATE = 40;
	public static final short NOT_FOUND = 41;
	public static final short INVALID_MONITOR = 50;
	public static final short NOT_MONITOR_OWNER = 51;
	public static final short INTERRUPT = 52;
	public static final short INVALID_CLASS_FORMAT = 60;
	public static final short CIRCULAR_CLASS_DEFINITION = 61;
	public static final short FAILS_VERIFICATION = 62;
	public static final short ADD_METHOD_NOT_IMPLEMENTED = 63;
	public static final short SCHEMA_CHANGE_NOT_IMPLEMENTED = 64;
	public static final short INVALID_TYPESTATE = 65;
	public static final short HIERARCHY_CHANGE_NOT_IMPLEMENTED = 66;
	public static final short DELETE_METHOD_NOT_IMPLEMENTED = 67;
	public static final short UNSUPPORTED_VERSION = 68;
	public static final short NAMES_DONT_MATCH = 69;
	public static final short CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 70;
	public static final short METHOD_MODIFIERS_CHANGE_NOT_IMPLEMENTED = 71;
	public static final short NOT_IMPLEMENTED = 99;
	public static final short NULL_POINTER = 100;
	public static final short ABSENT_INFORMATION = 101;
	public static final short INVALID_EVENT_TYPE = 102;
	public static final short ILLEGAL_ARGUMENT = 103;
	public static final short OUT_OF_MEMORY = 110;
	public static final short ACCESS_DENIED = 111;
	public static final short VM_DEAD = 112;
	public static final short INTERNAL = 113;
	public static final short UNATTACHED_THREAD = 115;
	public static final short INVALID_TAG = 500;
	public static final short ALREADY_INVOKING = 502;
	public static final short INVALID_INDEX = 503;
	public static final short INVALID_LENGTH = 504;
	public static final short INVALID_STRING = 506;
	public static final short INVALID_CLASS_LOADER = 507;
	public static final short INVALID_ARRAY = 508;
	public static final short TRANSPORT_LOAD = 509;
	public static final short TRANSPORT_INIT = 510;
	public static final short NATIVE_METHOD = 511;
	public static final short INVALID_COUNT = 512;
	public static final short HCR_OPERATION_REFUSED = 900; // HCR specific.

	/** Mapping of error codes to strings. */
	private static HashMap<Integer, String> fErrorMap = null;

	/** JDWP Error code. */
	private short fErrorCode;

	/**
	 * Creates new JdwpReplyPacket.
	 */
	public JdwpReplyPacket() {
		setFlags(FLAG_REPLY_PACKET);
	}

	/**
	 * @return Returns JDWP Error code.
	 */
	public short errorCode() {
		return fErrorCode;
	}

	/**
	 * Assigns JDWP Error code.
	 */
	public void setErrorCode(short newValue) {
		fErrorCode = newValue;
	}

	/**
	 * Reads header fields that are specific for this type of packet.
	 */
	@Override
	protected void readSpecificHeaderFields(DataInputStream dataInStream)
			throws IOException {
		fErrorCode = dataInStream.readShort();
	}

	/**
	 * Writes header fields that are specific for this type of packet.
	 */
	@Override
	protected void writeSpecificHeaderFields(DataOutputStream dataOutStream)
			throws IOException {
		dataOutStream.writeShort(fErrorCode);
	}

	/**
	 * Retrieves constant mappings.
	 */
	public static void getConstantMaps() {
		if (fErrorMap != null) {
			return;
		}

		Field[] fields = JdwpReplyPacket.class.getDeclaredFields();
		fErrorMap = new HashMap<Integer, String>(fields.length);
		for (Field field : fields) {
			if ((field.getModifiers() & Modifier.PUBLIC) == 0
					|| (field.getModifiers() & Modifier.STATIC) == 0
					|| (field.getModifiers() & Modifier.FINAL) == 0)
				continue;

			try {
				Integer intValue = new Integer(field.getInt(null));
				fErrorMap.put(intValue, field.getName());
			} catch (IllegalAccessException e) {
				// Will not occur for own class.
			} catch (IllegalArgumentException e) {
				// Should not occur.
				// We should take care that all public static final constants
				// in this class are numbers that are convertible to int.
			}
		}
	}

	/**
	 * @return Returns a map with string representations of error codes.
	 */
	public static Map<Integer, String> errorMap() {
		getConstantMaps();
		return fErrorMap;
	}
}
