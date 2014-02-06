/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Keith Seitz - Bug 165988
 *******************************************************************************/
package org.scalaide.debug.internal.spy;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UTFDataFormatException;
import java.util.Arrays;

import com.ibm.icu.text.MessageFormat;

/**
 * The <code>VerbosePacketWriter</code> is responsible for writing out
 * JdwpPacket data in human readable form.
 */
public class VerbosePacketStream extends PrintStream {
	/** Tag Constants. */
	// public static final byte NULL_TAG = 91; // Used for tagged null values.
	public static final byte ARRAY_TAG = 91; // '[' - an array object (objectID
												// size).
	public static final byte BYTE_TAG = 66; // 'B' - a byte value (1 byte).
	public static final byte CHAR_TAG = 67; // 'C' - a character value (2
											// bytes).
	public static final byte OBJECT_TAG = 76; // 'L' - an object (objectID
												// size).
	public static final byte FLOAT_TAG = 70; // 'F' - a float value (4 bytes).
	public static final byte DOUBLE_TAG = 68; // 'D' - a double value (8 bytes).
	public static final byte INT_TAG = 73; // 'I' - an int value (4 bytes).
	public static final byte LONG_TAG = 74; // 'J' - a long value (8 bytes).
	public static final byte SHORT_TAG = 83; // 'S' - a short value (2 bytes).
	public static final byte VOID_TAG = 86; // 'V' - a void value (no bytes).
	public static final byte BOOLEAN_TAG = 90; // 'Z' - a boolean value (1
												// byte).
	public static final byte STRING_TAG = 115; // 's' - a String object
												// (objectID size).
	public static final byte THREAD_TAG = 116; // 't' - a Thread object
												// (objectID size).
	public static final byte THREAD_GROUP_TAG = 103; // 'g' - a ThreadGroup
														// object (objectID
														// size).
	public static final byte CLASS_LOADER_TAG = 108; // 'l' - a ClassLoader
														// object (objectID
														// size).
	public static final byte CLASS_OBJECT_TAG = 99; // 'c' - a class object
													// object (objectID size).

	/** TypeTag Constants. */
	public static final byte TYPE_TAG_CLASS = 1; // ReferenceType is a class.
	public static final byte TYPE_TAG_INTERFACE = 2; // ReferenceType is an
														// interface.
	public static final byte TYPE_TAG_ARRAY = 3; // ReferenceType is an array.

	/** ClassStatus Constants. */
	public static final int JDWP_CLASS_STATUS_VERIFIED = 1;
	public static final int JDWP_CLASS_STATUS_PREPARED = 2;
	public static final int JDWP_CLASS_STATUS_INITIALIZED = 4;
	public static final int JDWP_CLASS_STATUS_ERROR = 8;

	/** access_flags Constants */
	public static final int ACC_PUBLIC = 0x0001;
	public static final int ACC_PRIVATE = 0x0002;
	public static final int ACC_PROTECTED = 0x0004;
	public static final int ACC_STATIC = 0x0008;
	public static final int ACC_FINAL = 0x0010;
	public static final int ACC_SUPER = 0x0020;
	public static final int ACC_VOLATILE = 0x0040;
	public static final int ACC_TRANSIENT = 0x0080;
	public static final int ACC_NATIVE = 0x0100;
	public static final int ACC_INTERFACE = 0x0200;
	public static final int ACC_ABSTRACT = 0x0400;
	public static final int ACC_STRICT = 0x0800;
	public static final int ACC_ENUM = 0x0100;
	public static final int ACC_VARARGS = 0x0080;
	public static final int ACC_BRIDGE = 0x0040;
	public static final int ACC_SYNTHETIC = 0x1000;
	public static final int ACC_SYNCHRONIZED = 0x0020;

	public static final int ACC_EXT_SYNTHETIC = 0xf0000000;

	/** Invoke options constants */
	public static final int INVOKE_SINGLE_THREADED = 0x01;
	public static final int INVOKE_NONVIRTUAL = 0x02;

	/** ThreadStatus Constants */
	public static final int THREAD_STATUS_ZOMBIE = 0;
	public static final int THREAD_STATUS_RUNNING = 1;
	public static final int THREAD_STATUS_SLEEPING = 2;
	public static final int THREAD_STATUS_MONITOR = 3;
	public static final int THREAD_STATUS_WAIT = 4;

	/** EventKind Constants */
	public static final int EVENTKIND_SINGLE_STEP = 1;
	public static final int EVENTKIND_BREAKPOINT = 2;
	public static final int EVENTKIND_FRAME_POP = 3;
	public static final int EVENTKIND_EXCEPTION = 4;
	public static final int EVENTKIND_USER_DEFINED = 5;
	public static final int EVENTKIND_THREAD_START = 6;
	public static final int EVENTKIND_THREAD_END = 7;
	public static final int EVENTKIND_THREAD_DEATH = EVENTKIND_THREAD_END;
	public static final int EVENTKIND_CLASS_PREPARE = 8;
	public static final int EVENTKIND_CLASS_UNLOAD = 9;
	public static final int EVENTKIND_CLASS_LOAD = 10;
	public static final int EVENTKIND_FIELD_ACCESS = 20;
	public static final int EVENTKIND_FIELD_MODIFICATION = 21;
	public static final int EVENTKIND_EXCEPTION_CATCH = 30;
	public static final int EVENTKIND_METHOD_ENTRY = 40;
	public static final int EVENTKIND_METHOD_EXIT = 41;
	public static final int EVENTKIND_VM_INIT = 90;
	public static final int EVENTKIND_VM_START = EVENTKIND_VM_INIT;
	public static final int EVENTKIND_VM_DEATH = 99;
	public static final int EVENTKIND_VM_DISCONNECTED = 100;

	/** SuspendStatus Constants */
	public static final int SUSPEND_STATUS_SUSPENDED = 0x01;

	/** SuspendPolicy Constants */
	public static final int SUSPENDPOLICY_NONE = 0;
	public static final int SUSPENDPOLICY_EVENT_THREAD = 1;
	public static final int SUSPENDPOLICY_ALL = 2;

	/** StepDepth Constants */
	public static final int STEPDEPTH_INTO = 0;
	public static final int STEPDEPTH_OVER = 1;
	public static final int STEPDEPTH_OUT = 2;

	/** StepSize Constants */
	public static final int STEPSIZE_MIN = 0;
	public static final int STEPSIZE_LINE = 1;

	private static final byte[] padding;
	static {
		padding = new byte[256];
		Arrays.fill(padding, (byte) ' ');
	}

	private static final String shift = new String(padding, 0, 32);

	public VerbosePacketStream(OutputStream out) {
		super(out);
	}

	private static final byte[] zeros;
	static {
		zeros = new byte[16];
		Arrays.fill(zeros, (byte) '0');
	}

	public synchronized void print(JdwpPacket packet, boolean fromVM)
			throws IOException {
		try {
			printHeader(packet, fromVM);
			printData(packet);
			println();
		} catch (UnableToParseDataException e) {
			println("\n" + e.getMessage() + ':'); //$NON-NLS-1$
			printDescription("Remaining data:"); //$NON-NLS-1$
			byte[] data = e.getRemainingData();
			if (data == null) {
				printHex(packet.data());
			} else {
				printHex(e.getRemainingData());
			}
			println();
		}
	}

	protected void printHeader(JdwpPacket packet, boolean fromVM)
			throws UnableToParseDataException {
		if (fromVM) {
			println("From VM"); //$NON-NLS-1$
		} else {
			println("From Debugger"); //$NON-NLS-1$
		}

		printDescription("Packet ID:"); //$NON-NLS-1$
		printHex(packet.getId());
		println();

		printDescription("Length:"); //$NON-NLS-1$
		print(packet.getLength());
		println();

		printDescription("Flags:"); //$NON-NLS-1$
		byte flags = packet.getFlags();
		printHex(flags);
		if ((flags & JdwpPacket.FLAG_REPLY_PACKET) != 0) {
			print(MessageFormat
					.format(" (REPLY to {0})", new Object[] { JdwpCommandPacket.commandMap().get(new Integer(TcpipSpy.getCommand(packet))) })); //$NON-NLS-1$
		} else {
			print(" (COMMAND)"); //$NON-NLS-1$
		}
		println();

		printSpecificHeaderFields(packet);
	}

	protected void printSpecificHeaderFields(JdwpPacket packet) {
		if (packet instanceof JdwpReplyPacket) {
			printError((JdwpReplyPacket) packet);
		} else if (packet instanceof JdwpCommandPacket) {
			printCommand((JdwpCommandPacket) packet);
		}
	}

	protected void printCommand(JdwpCommandPacket commandPacket) {
		printDescription("Command set:"); //$NON-NLS-1$
		int commandAndSet = commandPacket.getCommand();
		byte set = (byte) (commandAndSet >> 8);
		byte command = (byte) commandAndSet;
		printHex(set);
		printParanthetical(set);
		println();
		printDescription("Command:"); //$NON-NLS-1$
		printHex(command);
		printParanthetical(command);
		print(" ("); //$NON-NLS-1$
		print(JdwpCommandPacket.commandMap().get(new Integer(commandAndSet)));
		println(')');
	}

	protected void printError(JdwpReplyPacket reply) {
		int error = reply.errorCode();

		printDescription("Error:"); //$NON-NLS-1$
		printHex(error);
		if (error != 0) {
			print(" ("); //$NON-NLS-1$
			print(JdwpReplyPacket.errorMap().get(new Integer(error)));
			print(')');
		}
		println();
	}

	protected void printData(JdwpPacket packet) throws IOException,
			UnableToParseDataException {
		if ((packet.getFlags() & JdwpPacket.FLAG_REPLY_PACKET) != 0) {
			printReplyData((JdwpReplyPacket) packet);
		} else {
			printCommandData((JdwpCommandPacket) packet);
		}
	}

	private void printCommandData(JdwpCommandPacket command)
			throws IOException, UnableToParseDataException {
		byte[] data = command.data();
		if (data == null)
			return;
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		int commandId = command.getCommand();
		switch (commandId) {
		/** Commands VirtualMachine. */
		case JdwpCommandPacket.VM_VERSION:
			// no data
			break;
		case JdwpCommandPacket.VM_CLASSES_BY_SIGNATURE:
			printVmClassesBySignatureCommand(in);
			break;
		case JdwpCommandPacket.VM_ALL_CLASSES:
			// no data
			break;
		case JdwpCommandPacket.VM_ALL_THREADS:
			// no data
			break;
		case JdwpCommandPacket.VM_TOP_LEVEL_THREAD_GROUPS:
			// no data
			break;
		case JdwpCommandPacket.VM_DISPOSE:
			// no data
			break;
		case JdwpCommandPacket.VM_ID_SIZES:
			// no data
			break;
		case JdwpCommandPacket.VM_SUSPEND:
			// no data
			break;
		case JdwpCommandPacket.VM_RESUME:
			// no data
			break;
		case JdwpCommandPacket.VM_EXIT:
			printVmExitCommand(in);
			break;
		case JdwpCommandPacket.VM_CREATE_STRING:
			printVmCreateStringCommand(in);
			break;
		case JdwpCommandPacket.VM_CAPABILITIES:
			// no data
			break;
		case JdwpCommandPacket.VM_CLASS_PATHS:
			// no data
			break;
		case JdwpCommandPacket.VM_DISPOSE_OBJECTS:
			printVmDisposeObjectsCommand(in);
			break;
		case JdwpCommandPacket.VM_HOLD_EVENTS:
			// no data
			break;
		case JdwpCommandPacket.VM_RELEASE_EVENTS:
			// no data
			break;
		case JdwpCommandPacket.VM_CAPABILITIES_NEW:
			// no data
			break;
		case JdwpCommandPacket.VM_REDEFINE_CLASSES:
			printVmRedefineClassCommand(in);
			break;
		case JdwpCommandPacket.VM_SET_DEFAULT_STRATUM:
			printVmSetDefaultStratumCommand(in);
			break;
		case JdwpCommandPacket.VM_ALL_CLASSES_WITH_GENERIC:
			// no data
			break;

		/** Commands ReferenceType. */
		case JdwpCommandPacket.RT_SIGNATURE:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_CLASS_LOADER:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_MODIFIERS:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_FIELDS:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_METHODS:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_GET_VALUES:
			printRtGetValuesCommand(in);
			break;
		case JdwpCommandPacket.RT_SOURCE_FILE:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_NESTED_TYPES:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_STATUS:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_INTERFACES:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_CLASS_OBJECT:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_SOURCE_DEBUG_EXTENSION:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_SIGNATURE_WITH_GENERIC:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_FIELDS_WITH_GENERIC:
			printRtDefaultCommand(in);
			break;
		case JdwpCommandPacket.RT_METHODS_WITH_GENERIC:
			printRtDefaultCommand(in);
			break;

		/** Commands ClassType. */
		case JdwpCommandPacket.CT_SUPERCLASS:
			printCtSuperclassCommand(in);
			break;
		case JdwpCommandPacket.CT_SET_VALUES:
			printCtSetValuesCommand(in);
			break;
		case JdwpCommandPacket.CT_INVOKE_METHOD:
			printCtInvokeMethodCommand(in);
			break;
		case JdwpCommandPacket.CT_NEW_INSTANCE:
			printCtNewInstanceCommand(in);
			break;

		/** Commands ArrayType. */
		case JdwpCommandPacket.AT_NEW_INSTANCE:
			printAtNewInstanceCommand(in);
			break;

		/** Commands Method. */
		case JdwpCommandPacket.M_LINE_TABLE:
			printMDefaultCommand(in);
			break;
		case JdwpCommandPacket.M_VARIABLE_TABLE:
			printMDefaultCommand(in);
			break;
		case JdwpCommandPacket.M_BYTECODES:
			printMDefaultCommand(in);
			break;
		case JdwpCommandPacket.M_IS_OBSOLETE:
			printMDefaultCommand(in);
			break;
		case JdwpCommandPacket.M_VARIABLE_TABLE_WITH_GENERIC:
			printMDefaultCommand(in);
			break;

		/** Commands ObjectReference. */
		case JdwpCommandPacket.OR_REFERENCE_TYPE:
			printOrDefaultCommand(in);
			break;
		case JdwpCommandPacket.OR_GET_VALUES:
			printOrGetValuesCommand(in);
			break;
		case JdwpCommandPacket.OR_SET_VALUES:
			printOrSetValuesCommand(in);
			break;
		case JdwpCommandPacket.OR_MONITOR_INFO:
			printOrDefaultCommand(in);
			break;
		case JdwpCommandPacket.OR_INVOKE_METHOD:
			printOrInvokeMethodCommand(in);
			break;
		case JdwpCommandPacket.OR_DISABLE_COLLECTION:
			printOrDefaultCommand(in);
			break;
		case JdwpCommandPacket.OR_ENABLE_COLLECTION:
			printOrDefaultCommand(in);
			break;
		case JdwpCommandPacket.OR_IS_COLLECTED:
			printOrDefaultCommand(in);
			break;

		/** Commands StringReference. */
		case JdwpCommandPacket.SR_VALUE:
			printSrValueCommand(in);
			break;

		/** Commands ThreadReference. */
		case JdwpCommandPacket.TR_NAME:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_SUSPEND:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_RESUME:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_STATUS:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_THREAD_GROUP:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_FRAMES:
			printTrFramesCommand(in);
			break;
		case JdwpCommandPacket.TR_FRAME_COUNT:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_OWNED_MONITORS:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_CURRENT_CONTENDED_MONITOR:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_STOP:
			printTrStopCommand(in);
			break;
		case JdwpCommandPacket.TR_INTERRUPT:
			printTrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TR_SUSPEND_COUNT:
			printTrDefaultCommand(in);
			break;
		/*
		 * no more in the jdwp spec case JdwpCommandPacket.TR_POP_TOP_FRAME:
		 * break;
		 */

		/** Commands ThreadGroupReference. */
		case JdwpCommandPacket.TGR_NAME:
			printTgrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TGR_PARENT:
			printTgrDefaultCommand(in);
			break;
		case JdwpCommandPacket.TGR_CHILDREN:
			printTgrDefaultCommand(in);
			break;

		/** Commands ArrayReference. */
		case JdwpCommandPacket.AR_LENGTH:
			printArLengthCommand(in);
			break;
		case JdwpCommandPacket.AR_GET_VALUES:
			printArGetValuesCommand(in);
			break;
		case JdwpCommandPacket.AR_SET_VALUES:
			printArSetValuesCommand(in);
			break;

		/** Commands ClassLoaderReference. */
		case JdwpCommandPacket.CLR_VISIBLE_CLASSES:
			printClrVisibleClassesCommand(in);
			break;

		/** Commands EventRequest. */
		case JdwpCommandPacket.ER_SET:
			printErSetCommand(in);
			break;
		case JdwpCommandPacket.ER_CLEAR:
			printErClearCommand(in);
			break;
		case JdwpCommandPacket.ER_CLEAR_ALL_BREAKPOINTS:
			// no data
			break;

		/** Commands StackFrame. */
		case JdwpCommandPacket.SF_GET_VALUES:
			printSfGetValuesCommand(in);
			break;
		case JdwpCommandPacket.SF_SET_VALUES:
			printSfSetValuesCommand(in);
			break;
		case JdwpCommandPacket.SF_THIS_OBJECT:
			printSfDefaultCommand(in);
			break;
		case JdwpCommandPacket.SF_POP_FRAME:
			printSfDefaultCommand(in);
			break;

		/** Commands ClassObjectReference. */
		case JdwpCommandPacket.COR_REFLECTED_TYPE:
			printCorReflectedTypeCommand(in);
			break;

		/** Commands Event. */
		case JdwpCommandPacket.E_COMPOSITE:
			printECompositeCommand(in);
			break;

		/** Commands Hot Code Replacement (OTI specific). */
		case JdwpCommandPacket.HCR_CLASSES_HAVE_CHANGED:
		case JdwpCommandPacket.HCR_GET_CLASS_VERSION:
		case JdwpCommandPacket.HCR_DO_RETURN:
		case JdwpCommandPacket.HCR_REENTER_ON_EXIT:
		case JdwpCommandPacket.HCR_CAPABILITIES:
			throw new UnableToParseDataException(
					"NOT MANAGED COMMAND", remainderData(in)); //$NON-NLS-1$

		default:
			int cset = commandId >> 8;
			int cmd = commandId & 0xFF;
			println(MessageFormat
					.format("Unknown command : {0} {1}", new Object[] { "" + cset, "" + cmd })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
			break;
		}
	}

	private void printReplyData(JdwpReplyPacket reply) throws IOException,
			UnableToParseDataException {
		byte[] data = reply.data();
		if (data == null)
			return;
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
		JdwpCommandPacket command = TcpipSpy.getCommand(reply.getId());
		int commandId = command.getCommand();
		switch (commandId) {
		/** Commands VirtualMachine. */
		case JdwpCommandPacket.VM_VERSION:
			printVmVersionReply(in);
			break;
		case JdwpCommandPacket.VM_CLASSES_BY_SIGNATURE:
			printVmClassesBySignatureReply(in);
			break;
		case JdwpCommandPacket.VM_ALL_CLASSES:
			printVmAllClassesReply(in);
			break;
		case JdwpCommandPacket.VM_ALL_THREADS:
			printVmAllThreadsReply(in);
			break;
		case JdwpCommandPacket.VM_TOP_LEVEL_THREAD_GROUPS:
			printVmTopLevelThreadGroupReply(in);
			break;
		case JdwpCommandPacket.VM_DISPOSE:
			// no data
			break;
		case JdwpCommandPacket.VM_ID_SIZES:
			printVmIdSizesReply(in);
			break;
		case JdwpCommandPacket.VM_SUSPEND:
			// no data
			break;
		case JdwpCommandPacket.VM_RESUME:
			// no data
			break;
		case JdwpCommandPacket.VM_EXIT:
			// no data
			break;
		case JdwpCommandPacket.VM_CREATE_STRING:
			printVmCreateStringReply(in);
			break;
		case JdwpCommandPacket.VM_CAPABILITIES:
			printVmCapabilitiesReply(in);
			break;
		case JdwpCommandPacket.VM_CLASS_PATHS:
			printVmClassPathsReply(in);
			break;
		case JdwpCommandPacket.VM_DISPOSE_OBJECTS:
			// no data
			break;
		case JdwpCommandPacket.VM_HOLD_EVENTS:
			// no data
			break;
		case JdwpCommandPacket.VM_RELEASE_EVENTS:
			// no data
			break;
		case JdwpCommandPacket.VM_CAPABILITIES_NEW:
			printVmCapabilitiesNewReply(in);
			break;
		case JdwpCommandPacket.VM_REDEFINE_CLASSES:
			// no data
			break;
		case JdwpCommandPacket.VM_SET_DEFAULT_STRATUM:
			// no data
			break;
		case JdwpCommandPacket.VM_ALL_CLASSES_WITH_GENERIC:
			printVmAllClassesWithGenericReply(in);
			break;

		/** Commands ReferenceType. */
		case JdwpCommandPacket.RT_SIGNATURE:
			printRtSignatureReply(in);
			break;
		case JdwpCommandPacket.RT_CLASS_LOADER:
			printRtClassLoaderReply(in);
			break;
		case JdwpCommandPacket.RT_MODIFIERS:
			printRtModifiersReply(in);
			break;
		case JdwpCommandPacket.RT_FIELDS:
			printRtFieldsReply(in);
			break;
		case JdwpCommandPacket.RT_METHODS:
			printRtMethodsReply(in);
			break;
		case JdwpCommandPacket.RT_GET_VALUES:
			printRtGetValuesReply(in);
			break;
		case JdwpCommandPacket.RT_SOURCE_FILE:
			printRtSourceFileReply(in);
			break;
		case JdwpCommandPacket.RT_NESTED_TYPES:
			printRtNestedTypesReply(in);
			break;
		case JdwpCommandPacket.RT_STATUS:
			printRtStatusReply(in);
			break;
		case JdwpCommandPacket.RT_INTERFACES:
			printRtInterfacesReply(in);
			break;
		case JdwpCommandPacket.RT_CLASS_OBJECT:
			printRtClassObjectReply(in);
			break;
		case JdwpCommandPacket.RT_SOURCE_DEBUG_EXTENSION:
			printRtSourceDebugExtensionReply(in);
			break;
		case JdwpCommandPacket.RT_SIGNATURE_WITH_GENERIC:
			printRtSignatureWithGenericReply(in);
			break;
		case JdwpCommandPacket.RT_FIELDS_WITH_GENERIC:
			printRtFieldsWithGenericReply(in);
			break;
		case JdwpCommandPacket.RT_METHODS_WITH_GENERIC:
			printRtMethodsWithGenericReply(in);
			break;

		/** Commands ClassType. */
		case JdwpCommandPacket.CT_SUPERCLASS:
			printCtSuperclassReply(in);
			break;
		case JdwpCommandPacket.CT_SET_VALUES:
			// no data
			break;
		case JdwpCommandPacket.CT_INVOKE_METHOD:
			printCtInvokeMethodReply(in);
			break;
		case JdwpCommandPacket.CT_NEW_INSTANCE:
			printCtNewInstanceReply(in);
			break;

		/** Commands ArrayType. */
		case JdwpCommandPacket.AT_NEW_INSTANCE:
			printAtNewInstanceReply(in);
			break;

		/** Commands Method. */
		case JdwpCommandPacket.M_LINE_TABLE:
			printMLineTableReply(in);
			break;
		case JdwpCommandPacket.M_VARIABLE_TABLE:
			printMVariableTableReply(in);
			break;
		case JdwpCommandPacket.M_BYTECODES:
			printMBytecodesReply(in);
			break;
		case JdwpCommandPacket.M_IS_OBSOLETE:
			printMIsObsoleteReply(in);
			break;
		case JdwpCommandPacket.M_VARIABLE_TABLE_WITH_GENERIC:
			printMVariableTableWithGenericReply(in);
			break;

		/** Commands ObjectReference. */
		case JdwpCommandPacket.OR_REFERENCE_TYPE:
			printOrReferenceTypeReply(in);
			break;
		case JdwpCommandPacket.OR_GET_VALUES:
			printOrGetValuesReply(in);
			break;
		case JdwpCommandPacket.OR_SET_VALUES:
			// no data
			break;
		case JdwpCommandPacket.OR_MONITOR_INFO:
			printOrMonitorInfoReply(in);
			break;
		case JdwpCommandPacket.OR_INVOKE_METHOD:
			printOrInvokeMethodReply(in);
			break;
		case JdwpCommandPacket.OR_DISABLE_COLLECTION:
			// no data
			break;
		case JdwpCommandPacket.OR_ENABLE_COLLECTION:
			// no data
			break;
		case JdwpCommandPacket.OR_IS_COLLECTED:
			printOrIsCollectedReply(in);
			break;

		/** Commands StringReference. */
		case JdwpCommandPacket.SR_VALUE:
			printSrValueReply(in);
			break;

		/** Commands ThreadReference. */
		case JdwpCommandPacket.TR_NAME:
			printTrNameReply(in);
			break;
		case JdwpCommandPacket.TR_SUSPEND:
			// no data
			break;
		case JdwpCommandPacket.TR_RESUME:
			// no data
			break;
		case JdwpCommandPacket.TR_STATUS:
			printTrStatusReply(in);
			break;
		case JdwpCommandPacket.TR_THREAD_GROUP:
			printTrThreadGroupReply(in);
			break;
		case JdwpCommandPacket.TR_FRAMES:
			printTrFramesReply(in);
			break;
		case JdwpCommandPacket.TR_FRAME_COUNT:
			printTrFrameCountReply(in);
			break;
		case JdwpCommandPacket.TR_OWNED_MONITORS:
			printTrOwnedMonitorsReply(in);
			break;
		case JdwpCommandPacket.TR_CURRENT_CONTENDED_MONITOR:
			printTrCurrentContendedMonitorReply(in);
			break;
		case JdwpCommandPacket.TR_STOP:
			// no data
			break;
		case JdwpCommandPacket.TR_INTERRUPT:
			// no data
			break;
		case JdwpCommandPacket.TR_SUSPEND_COUNT:
			printTrSuspendCountReply(in);
			break;
		/*
		 * no more in the jdwp spec case JdwpCommandPacket.TR_POP_TOP_FRAME:
		 * break;
		 */
		/** Commands ThreadGroupReference. */
		case JdwpCommandPacket.TGR_NAME:
			printTgrNameReply(in);
			break;
		case JdwpCommandPacket.TGR_PARENT:
			printTgrParentReply(in);
			break;
		case JdwpCommandPacket.TGR_CHILDREN:
			printTgrChildrenReply(in);
			break;

		/** Commands ArrayReference. */
		case JdwpCommandPacket.AR_LENGTH:
			printArLengthReply(in);
			break;
		case JdwpCommandPacket.AR_GET_VALUES:
			printArGetValuesReply(in);
			break;
		case JdwpCommandPacket.AR_SET_VALUES:
			// no data
			break;

		/** Commands ClassLoaderReference. */
		case JdwpCommandPacket.CLR_VISIBLE_CLASSES:
			printClrVisibleClassesReply(in);
			break;

		/** Commands EventRequest. */
		case JdwpCommandPacket.ER_SET:
			printErSetReply(in);
			break;
		case JdwpCommandPacket.ER_CLEAR:
			// no data
			break;
		case JdwpCommandPacket.ER_CLEAR_ALL_BREAKPOINTS:
			// no data
			break;

		/** Commands StackFrame. */
		case JdwpCommandPacket.SF_GET_VALUES:
			printSfGetValuesReply(in);
			break;
		case JdwpCommandPacket.SF_SET_VALUES:
			// no data
			break;
		case JdwpCommandPacket.SF_THIS_OBJECT:
			printSfThisObjectReply(in);
			break;
		case JdwpCommandPacket.SF_POP_FRAME:
			// no data
			break;

		/** Commands ClassObjectReference. */
		case JdwpCommandPacket.COR_REFLECTED_TYPE:
			printCorReflectedTypeReply(in);
			break;

		/** Commands Event. */
		/*
		 * no reply case JdwpCommandPacket.E_COMPOSITE: break;
		 */

		/** Commands Hot Code Replacement (OTI specific). */
		case JdwpCommandPacket.HCR_CLASSES_HAVE_CHANGED:
		case JdwpCommandPacket.HCR_GET_CLASS_VERSION:
		case JdwpCommandPacket.HCR_DO_RETURN:
		case JdwpCommandPacket.HCR_REENTER_ON_EXIT:
		case JdwpCommandPacket.HCR_CAPABILITIES:
			throw new UnableToParseDataException(
					"NOT MANAGED COMMAND", remainderData(in)); //$NON-NLS-1$

		default:
			int cset = commandId >> 8;
			int cmd = commandId & 0xFF;
			println(MessageFormat
					.format("Unknown command : {0} {1}", new Object[] { "" + cset, "" + cmd })); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ 
			break;
		}
	}

	private void printRefTypeTag(byte refTypeTag) {
		printDescription("Type tag:"); //$NON-NLS-1$
		printRefTypeTagValue(refTypeTag);
		println();
	}

	private void printRefTypeTagValue(byte refTypeTag) {
		printHex(refTypeTag);
		print(" ("); //$NON-NLS-1$
		switch (refTypeTag) {
		case TYPE_TAG_CLASS:
			print("CLASS"); //$NON-NLS-1$
			break;
		case TYPE_TAG_INTERFACE:
			print("INTERFACE"); //$NON-NLS-1$
			break;
		case TYPE_TAG_ARRAY:
			print("ARRAY"); //$NON-NLS-1$
			break;
		default:
			print("unknown"); //$NON-NLS-1$
		}
		print(')');
	}

	private void printClassStatus(int status) {
		printDescription("Status:"); //$NON-NLS-1$
		printHex(status);
		print(" ("); //$NON-NLS-1$
		boolean spaceNeeded = false;
		if ((status & JDWP_CLASS_STATUS_VERIFIED) != 0) {
			print("VERIFIED"); //$NON-NLS-1$
			spaceNeeded = true;
		}
		if ((status & JDWP_CLASS_STATUS_PREPARED) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PREPARED"); //$NON-NLS-1$
		}
		if ((status & JDWP_CLASS_STATUS_INITIALIZED) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("INITIALIZED"); //$NON-NLS-1$
		}
		if ((status & JDWP_CLASS_STATUS_ERROR) != 0) {
			if (spaceNeeded) {
				print(' ');
			}
			print("unknown"); //$NON-NLS-1$
		}
		println(')');
	}

	private void printClassModifiers(int modifiers) {
		printDescription("Modifiers:"); //$NON-NLS-1$
		printHex(modifiers);
		print(" ("); //$NON-NLS-1$
		boolean spaceNeeded = false;
		if ((modifiers & ACC_PUBLIC) != 0) {
			print("PUBLIC"); //$NON-NLS-1$
			spaceNeeded = true;
		}
		if ((modifiers & ACC_PRIVATE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PRIVATE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_PROTECTED) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PROTECTED"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_STATIC) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("STATIC"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_FINAL) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("FINAL"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_SUPER) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("SUPER"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_INTERFACE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("INTERFACE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_ABSTRACT) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("ABSTRACT"); //$NON-NLS-1$
		}
		if ((modifiers & (ACC_EXT_SYNTHETIC | ACC_SYNTHETIC)) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("SYNTHETIC"); //$NON-NLS-1$
		}
		println(')');
	}

	private void printMethodModifiers(int modifiers) {
		printDescription("Modifiers:"); //$NON-NLS-1$
		printHex(modifiers);
		print(" ("); //$NON-NLS-1$
		boolean spaceNeeded = false;
		if ((modifiers & ACC_PUBLIC) != 0) {
			print("PUBLIC"); //$NON-NLS-1$
			spaceNeeded = true;
		}
		if ((modifiers & ACC_PRIVATE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PRIVATE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_PROTECTED) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PROTECTED"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_STATIC) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("STATIC"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_FINAL) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("FINAL"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_SYNCHRONIZED) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("SYNCHRONIZED"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_BRIDGE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("BRIDGE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_VARARGS) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("VARARGS"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_NATIVE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("NATIVE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_ABSTRACT) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("ABSTRACT"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_STRICT) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("STRICT"); //$NON-NLS-1$
		}
		if ((modifiers & (ACC_EXT_SYNTHETIC | ACC_SYNTHETIC)) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("SYNTHETIC"); //$NON-NLS-1$
		}
		println(')');
	}

	private void printFieldModifiers(int modifiers) {
		printDescription("Modifiers:"); //$NON-NLS-1$
		printHex(modifiers);
		print(" ("); //$NON-NLS-1$
		boolean spaceNeeded = false;
		if ((modifiers & ACC_PUBLIC) != 0) {
			print("PUBLIC"); //$NON-NLS-1$
			spaceNeeded = true;
		}
		if ((modifiers & ACC_PRIVATE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PRIVATE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_PROTECTED) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("PROTECTED"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_STATIC) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("STATIC"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_FINAL) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("FINAL"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_VOLATILE) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("VOLATILE"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_TRANSIENT) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("TRANSIENT"); //$NON-NLS-1$
		}
		if ((modifiers & ACC_ENUM) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("ENUM"); //$NON-NLS-1$
		}
		if ((modifiers & (ACC_EXT_SYNTHETIC | ACC_SYNTHETIC)) != 0) {
			if (spaceNeeded) {
				print(' ');
			} else {
				spaceNeeded = true;
			}
			print("SYNTHETIC"); //$NON-NLS-1$
		}
		println(')');
	}

	private void printInvocationOptions(int invocationOptions) {
		printDescription("Invocation Options:"); //$NON-NLS-1$
		printHex(invocationOptions);
		print(" ("); //$NON-NLS-1$
		boolean spaceNeeded = false;
		if ((invocationOptions & INVOKE_SINGLE_THREADED) != 0) {
			print("SINGLE_THREADED"); //$NON-NLS-1$
			spaceNeeded = true;
		}
		if ((invocationOptions & INVOKE_NONVIRTUAL) != 0) {
			if (spaceNeeded) {
				print(' ');
			}
			print("NONVIRTUAL"); //$NON-NLS-1$
		}
		println(')');
	}

	private void printThreadStatus(int threadStatus) {
		printDescription("Thread status:"); //$NON-NLS-1$
		printHex(threadStatus);
		print(" ("); //$NON-NLS-1$
		switch (threadStatus) {
		case THREAD_STATUS_ZOMBIE:
			print("ZOMBIE"); //$NON-NLS-1$
			break;
		case THREAD_STATUS_RUNNING:
			print("RUNNING"); //$NON-NLS-1$
			break;
		case THREAD_STATUS_SLEEPING:
			print("SLEEPING"); //$NON-NLS-1$
			break;
		case THREAD_STATUS_MONITOR:
			print("MONITOR"); //$NON-NLS-1$
			break;
		case THREAD_STATUS_WAIT:
			print("WAIT"); //$NON-NLS-1$
			break;
		default:
			print("unknown"); //$NON-NLS-1$
			break;
		}
		println(')');
	}

	private void printSuspendStatus(int suspendStatus) {
		printDescription("Suspend status:"); //$NON-NLS-1$
		printHex(suspendStatus);
		print(" ("); //$NON-NLS-1$
		if ((suspendStatus & SUSPEND_STATUS_SUSPENDED) != 0) {
			print("SUSPENDED"); //$NON-NLS-1$
		}
		println(')');
	}

	private void printEventKind(byte eventKind) {
		printDescription("Event kind:"); //$NON-NLS-1$
		printHex(eventKind);
		print(" ("); //$NON-NLS-1$
		switch (eventKind) {
		case EVENTKIND_SINGLE_STEP:
			print("SINGLE_STEP"); //$NON-NLS-1$
			break;
		case EVENTKIND_BREAKPOINT:
			print("BREAKPOINT"); //$NON-NLS-1$
			break;
		case EVENTKIND_FRAME_POP:
			print("FRAME_POP"); //$NON-NLS-1$
			break;
		case EVENTKIND_EXCEPTION:
			print("EXCEPTION"); //$NON-NLS-1$
			break;
		case EVENTKIND_USER_DEFINED:
			print("USER_DEFINED"); //$NON-NLS-1$
			break;
		case EVENTKIND_THREAD_START:
			print("THREAD_START"); //$NON-NLS-1$
			break;
		case EVENTKIND_THREAD_END:
			print("THREAD_END"); //$NON-NLS-1$
			break;
		case EVENTKIND_CLASS_PREPARE:
			print("CLASS_PREPARE"); //$NON-NLS-1$
			break;
		case EVENTKIND_CLASS_UNLOAD:
			print("CLASS_UNLOAD"); //$NON-NLS-1$
			break;
		case EVENTKIND_CLASS_LOAD:
			print("CLASS_LOAD"); //$NON-NLS-1$
			break;
		case EVENTKIND_FIELD_ACCESS:
			print("FIELD_ACCESS"); //$NON-NLS-1$
			break;
		case EVENTKIND_FIELD_MODIFICATION:
			print("FIELD_MODIFICATION"); //$NON-NLS-1$
			break;
		case EVENTKIND_EXCEPTION_CATCH:
			print("EXCEPTION_CATCH"); //$NON-NLS-1$
			break;
		case EVENTKIND_METHOD_ENTRY:
			print("METHOD_ENTRY"); //$NON-NLS-1$
			break;
		case EVENTKIND_METHOD_EXIT:
			print("METHOD_EXIT"); //$NON-NLS-1$
			break;
		case EVENTKIND_VM_INIT:
			print("VM_INIT"); //$NON-NLS-1$
			break;
		case EVENTKIND_VM_DEATH:
			print("VM_DEATH"); //$NON-NLS-1$
			break;
		case EVENTKIND_VM_DISCONNECTED:
			print("VM_DISCONNECTED"); //$NON-NLS-1$
			break;
		default:
			print("unknown"); //$NON-NLS-1$
			break;
		}
		println(')');
	}

	private void printSuspendPolicy(byte suspendPolicy) {
		printDescription("Suspend policy:"); //$NON-NLS-1$
		printHex(suspendPolicy);
		print(" ("); //$NON-NLS-1$
		switch (suspendPolicy) {
		case SUSPENDPOLICY_NONE:
			print("NONE"); //$NON-NLS-1$
			break;
		case SUSPENDPOLICY_EVENT_THREAD:
			print("EVENT_THREAD"); //$NON-NLS-1$
			break;
		case SUSPENDPOLICY_ALL:
			print("ALL"); //$NON-NLS-1$
			break;
		default:
			print("unknown"); //$NON-NLS-1$
			break;
		}
		println(')');
	}

	private void printStepDepth(int setDepth) {
		printDescription("Step depth:"); //$NON-NLS-1$
		printHex(setDepth);
		print(" ("); //$NON-NLS-1$
		switch (setDepth) {
		case STEPDEPTH_INTO:
			print("INTO"); //$NON-NLS-1$
			break;
		case STEPDEPTH_OVER:
			print("OVER"); //$NON-NLS-1$
			break;
		case STEPDEPTH_OUT:
			print("OUT"); //$NON-NLS-1$
			break;
		default:
			print("unknown"); //$NON-NLS-1$
			break;
		}
		println(')');
	}

	private void printStepSize(int setSize) {
		printDescription("Step size:"); //$NON-NLS-1$
		printHex(setSize);
		print(" ("); //$NON-NLS-1$
		switch (setSize) {
		case STEPSIZE_MIN:
			print("MIN"); //$NON-NLS-1$
			break;
		case STEPSIZE_LINE:
			print("LINE"); //$NON-NLS-1$
			break;
		default:
			print("unknown"); //$NON-NLS-1$
			break;
		}
		println(')');
	}

	private void printVmVersionReply(DataInputStream in) throws IOException {
		String description = readString(in);
		int jdwpMajor = in.readInt();
		int jdwpMinor = in.readInt();
		String vmVersion = readString(in);
		String vmName = readString(in);

		println("VM Description:", description); //$NON-NLS-1$
		println("JDWP Major Version:", jdwpMajor); //$NON-NLS-1$
		println("JDWP Minor Version:", jdwpMinor); //$NON-NLS-1$
		println("VM Version:", vmVersion); //$NON-NLS-1$
		println("VM Name:", vmName); //$NON-NLS-1$
	}

	private void printVmClassesBySignatureCommand(DataInputStream in)
			throws IOException {
		String signature = readString(in);
		println("Class signature:", signature); //$NON-NLS-1$
	}

	private void printVmClassesBySignatureReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int classesCount = in.readInt();
		println("Classes count:", classesCount); //$NON-NLS-1$
		for (int i = 0; i < classesCount; i++) {
			byte refTypeTag = in.readByte();
			long typeId = readReferenceTypeID(in);
			int status = in.readInt();
			printRefTypeTag(refTypeTag);
			printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
			printClassStatus(status);
		}
	}

	private void printVmAllClassesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int classesCount = in.readInt();
		println("Classes count:", classesCount); //$NON-NLS-1$
		for (int i = 0; i < classesCount; i++) {
			byte refTypeTag = in.readByte();
			long typeId = readReferenceTypeID(in);
			String signature = readString(in);
			int status = in.readInt();
			printRefTypeTag(refTypeTag);
			printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
			println("Class signature:", signature); //$NON-NLS-1$
			printClassStatus(status);
		}
	}

	private void printVmAllThreadsReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int threadsCount = in.readInt();
		println("Threads count:", threadsCount); //$NON-NLS-1$
		for (int i = 0; i < threadsCount; i++) {
			long threadId = readObjectID(in);
			printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
		}
	}

	private void printVmTopLevelThreadGroupReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int groupsCount = in.readInt();
		println("Threads count:", groupsCount); //$NON-NLS-1$
		for (int i = 0; i < groupsCount; i++) {
			long threadGroupId = readObjectID(in);
			printlnObjectId("Thread id:", threadGroupId); //$NON-NLS-1$
		}
	}

	private void printVmIdSizesReply(DataInputStream in) throws IOException {
		int fieldIDSize = in.readInt();
		int methodIDSize = in.readInt();
		int objectIDSize = in.readInt();
		int referenceTypeIDSize = in.readInt();
		int frameIDSize = in.readInt();
		println("Field ID size:", fieldIDSize); //$NON-NLS-1$
		println("Method ID size:", methodIDSize); //$NON-NLS-1$
		println("Object ID size:", objectIDSize); //$NON-NLS-1$
		println("Reference type ID size:", referenceTypeIDSize); //$NON-NLS-1$
		println("Frame ID size:", frameIDSize); //$NON-NLS-1$
		TcpipSpy.setFieldIDSize(fieldIDSize);
		TcpipSpy.setMethodIDSize(methodIDSize);
		TcpipSpy.setObjectIDSize(objectIDSize);
		TcpipSpy.setReferenceTypeIDSize(referenceTypeIDSize);
		TcpipSpy.setFrameIDSize(frameIDSize);
		TcpipSpy.setHasSizes(true);
	}

	private void printVmExitCommand(DataInputStream in) throws IOException {
		int exitCode = in.readInt();
		println("Exit code:", exitCode); //$NON-NLS-1$
	}

	private void printVmCreateStringCommand(DataInputStream in)
			throws IOException {
		String string = readString(in);
		println("String:", string); //$NON-NLS-1$
	}

	private void printVmCreateStringReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long stringId = readObjectID(in);
		printlnObjectId("String id:", stringId); //$NON-NLS-1$
	}

	private void printVmCapabilitiesReply(DataInputStream in)
			throws IOException {
		boolean canWatchFieldModification = in.readBoolean();
		boolean canWatchFieldAccess = in.readBoolean();
		boolean canGetBytecodes = in.readBoolean();
		boolean canGetSyntheticAttribute = in.readBoolean();
		boolean canGetOwnedMonitorInfo = in.readBoolean();
		boolean canGetCurrentContendedMonitor = in.readBoolean();
		boolean canGetMonitorInfo = in.readBoolean();
		println("Can watch field modification:", canWatchFieldModification); //$NON-NLS-1$
		println("can watch field access:", canWatchFieldAccess); //$NON-NLS-1$
		println("Can get bytecodes:", canGetBytecodes); //$NON-NLS-1$
		println("Can get synthetic attribute:", canGetSyntheticAttribute); //$NON-NLS-1$
		println("Can get owned monitor info:", canGetOwnedMonitorInfo); //$NON-NLS-1$
		println("Can get currently contended monitor:", canGetCurrentContendedMonitor); //$NON-NLS-1$
		println("Can get monitor info:", canGetMonitorInfo); //$NON-NLS-1$
	}

	private void printVmClassPathsReply(DataInputStream in) throws IOException {
		String baseDir = readString(in);
		println("Base directory:", baseDir); //$NON-NLS-1$
		int classpathCount = in.readInt();
		println("Classpaths count:", classpathCount); //$NON-NLS-1$
		for (int i = 0; i < classpathCount; i++) {
			String path = readString(in);
			println("Classpath:", path); //$NON-NLS-1$
		}
		int bootclasspathCount = in.readInt();
		println("Bootclasspaths count:", bootclasspathCount); //$NON-NLS-1$
		for (int i = 0; i < bootclasspathCount; i++) {
			String path = readString(in);
			println("Bootclasspath:", path); //$NON-NLS-1$
		}
	}

	private void printVmDisposeObjectsCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int requestsCount = in.readInt();
		println("Requests Count:", requestsCount); //$NON-NLS-1$
		for (int i = 0; i < requestsCount; i++) {
			long objectId = readObjectID(in);
			int refsCounts = in.readInt();
			printlnObjectId("Object id:", objectId); //$NON-NLS-1$
			println("References count:", refsCounts); //$NON-NLS-1$
		}
	}

	private void printVmCapabilitiesNewReply(DataInputStream in)
			throws IOException {
		printVmCapabilitiesReply(in);
		boolean canRedefineClasses = in.readBoolean();
		boolean canAddMethod = in.readBoolean();
		boolean canUnrestrictedlyRedefineClasses = in.readBoolean();
		boolean canPopFrames = in.readBoolean();
		boolean canUseInstanceFilters = in.readBoolean();
		boolean canGetSourceDebugExtension = in.readBoolean();
		boolean canRequestVMDeathEvent = in.readBoolean();
		boolean canSetDefaultStratum = in.readBoolean();
		boolean reserved16 = in.readBoolean();
		boolean reserved17 = in.readBoolean();
		boolean reserved18 = in.readBoolean();
		boolean reserved19 = in.readBoolean();
		boolean reserved20 = in.readBoolean();
		boolean reserved21 = in.readBoolean();
		boolean reserved22 = in.readBoolean();
		boolean reserved23 = in.readBoolean();
		boolean reserved24 = in.readBoolean();
		boolean reserved25 = in.readBoolean();
		boolean reserved26 = in.readBoolean();
		boolean reserved27 = in.readBoolean();
		boolean reserved28 = in.readBoolean();
		boolean reserved29 = in.readBoolean();
		boolean reserved30 = in.readBoolean();
		boolean reserved31 = in.readBoolean();
		boolean reserved32 = in.readBoolean();
		println("Can redefine classes:", canRedefineClasses); //$NON-NLS-1$
		println("Can add method:", canAddMethod); //$NON-NLS-1$
		println("Can unrestrictedly rd. classes:", canUnrestrictedlyRedefineClasses); //$NON-NLS-1$
		println("Can pop frames:", canPopFrames); //$NON-NLS-1$
		println("Can use instance filters:", canUseInstanceFilters); //$NON-NLS-1$
		println("Can get source debug extension:", canGetSourceDebugExtension); //$NON-NLS-1$
		println("Can request VMDeath event:", canRequestVMDeathEvent); //$NON-NLS-1$
		println("Can set default stratum:", canSetDefaultStratum); //$NON-NLS-1$
		println("Reserved:", reserved16); //$NON-NLS-1$
		println("Reserved:", reserved17); //$NON-NLS-1$
		println("Reserved:", reserved18); //$NON-NLS-1$
		println("Reserved:", reserved19); //$NON-NLS-1$
		println("Reserved:", reserved20); //$NON-NLS-1$
		println("Reserved:", reserved21); //$NON-NLS-1$
		println("Reserved:", reserved22); //$NON-NLS-1$
		println("Reserved:", reserved23); //$NON-NLS-1$
		println("Reserved:", reserved24); //$NON-NLS-1$
		println("Reserved:", reserved25); //$NON-NLS-1$
		println("Reserved:", reserved26); //$NON-NLS-1$
		println("Reserved:", reserved27); //$NON-NLS-1$
		println("Reserved:", reserved28); //$NON-NLS-1$
		println("Reserved:", reserved29); //$NON-NLS-1$
		println("Reserved:", reserved30); //$NON-NLS-1$
		println("Reserved:", reserved31); //$NON-NLS-1$
		println("Reserved:", reserved32); //$NON-NLS-1$
	}

	private void printVmRedefineClassCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int typesCount = in.readInt();
		println("Types count:", typesCount); //$NON-NLS-1$
		for (int i = 0; i < typesCount; i++) {
			long typeId = readReferenceTypeID(in);
			int classfileLength = in.readInt();
			printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
			println("Classfile length:", classfileLength); //$NON-NLS-1$
			while ((classfileLength -= in.skipBytes(classfileLength)) != 0) {
			}
			printDescription("Class bytes:"); //$NON-NLS-1$
			println("skipped"); //$NON-NLS-1$
		}
	}

	private void printVmSetDefaultStratumCommand(DataInputStream in)
			throws IOException {
		String stratumId = readString(in);
		println("Stratum id:", stratumId); //$NON-NLS-1$
	}

	private void printVmAllClassesWithGenericReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int classesCount = in.readInt();
		println("Classes count:", classesCount); //$NON-NLS-1$
		for (int i = 0; i < classesCount; i++) {
			byte refTypeTag = in.readByte();
			long typeId = readReferenceTypeID(in);
			String signature = readString(in);
			String genericSignature = readString(in);
			int status = in.readInt();
			printRefTypeTag(refTypeTag);
			printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
			println("Class signature:", signature); //$NON-NLS-1$
			println("Generic class signature:", genericSignature); //$NON-NLS-1$
			printClassStatus(status);
		}
	}

	private void printRtDefaultCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long typeId = readReferenceTypeID(in);
		printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
	}

	private void printRtSignatureReply(DataInputStream in) throws IOException {
		String signature = readString(in);
		println("Signature:", signature); //$NON-NLS-1$
	}

	private void printRtClassLoaderReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classLoaderId = readObjectID(in);
		printlnObjectId("ClassLoader id:", classLoaderId); //$NON-NLS-1$
	}

	private void printRtModifiersReply(DataInputStream in) throws IOException {
		int modifiers = in.readInt();
		printClassModifiers(modifiers);
	}

	private void printRtFieldsReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int fieldsCount = in.readInt();
		println("Fields count:", fieldsCount); //$NON-NLS-1$
		for (int i = 0; i < fieldsCount; i++) {
			long fieldId = readFieldID(in);
			String name = readString(in);
			String signature = readString(in);
			int modifiers = in.readInt();
			printlnFieldId("Field id:", fieldId); //$NON-NLS-1$
			println("Name:", name); //$NON-NLS-1$
			println("Signature:", signature); //$NON-NLS-1$
			printFieldModifiers(modifiers);
		}
	}

	private void printRtMethodsReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int methodsCount = in.readInt();
		println("Methods count:", methodsCount); //$NON-NLS-1$
		for (int i = 0; i < methodsCount; i++) {
			long methodId = readMethodID(in);
			String name = readString(in);
			String signature = readString(in);
			int modifiers = in.readInt();
			printlnMethodId("Method id:", methodId); //$NON-NLS-1$
			println("Name:", name); //$NON-NLS-1$
			println("Signature:", signature); //$NON-NLS-1$
			printMethodModifiers(modifiers);
		}
	}

	private void printRtGetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long typeId = readReferenceTypeID(in);
		int fieldsCount = in.readInt();
		printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
		println("Fields count:", fieldsCount); //$NON-NLS-1$
		for (int i = 0; i < fieldsCount; i++) {
			long fieldId = readFieldID(in);
			printlnFieldId("Field id:", fieldId); //$NON-NLS-1$
		}
	}

	private void printRtGetValuesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int valuesCount = in.readInt();
		println("Values count:", valuesCount); //$NON-NLS-1$
		for (int i = 0; i < valuesCount; i++) {
			readAndPrintlnTaggedValue("Value:", in); //$NON-NLS-1$
		}
	}

	private void printRtSourceFileReply(DataInputStream in) throws IOException {
		String sourceFile = readString(in);
		println("Source file:", sourceFile); //$NON-NLS-1$
	}

	private void printRtNestedTypesReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int typesCount = in.readInt();
		println("Types count:", typesCount); //$NON-NLS-1$
		for (int i = 0; i < typesCount; i++) {
			byte typeTag = in.readByte();
			long typeId = readReferenceTypeID(in);
			printRefTypeTag(typeTag);
			printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
		}
	}

	private void printRtStatusReply(DataInputStream in) throws IOException {
		int status = in.readInt();
		printClassStatus(status);
	}

	private void printRtInterfacesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int interfacesCount = in.readInt();
		println("Interfaces count:", interfacesCount); //$NON-NLS-1$
		for (int i = 0; i < interfacesCount; i++) {
			long interfaceId = readReferenceTypeID(in);
			printlnReferenceTypeId("Interface type id:", interfaceId); //$NON-NLS-1$
		}
	}

	private void printRtClassObjectReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classObjectId = readObjectID(in);
		printlnObjectId("Class object id:", classObjectId); //$NON-NLS-1$
	}

	private void printRtSourceDebugExtensionReply(DataInputStream in)
			throws IOException {
		String extension = readString(in);
		println("Extension:", extension); //$NON-NLS-1$
	}

	private void printRtSignatureWithGenericReply(DataInputStream in)
			throws IOException {
		String signature = readString(in);
		String genericSignature = readString(in);
		println("Signature:", signature); //$NON-NLS-1$
		println("Generic signature:", genericSignature); //$NON-NLS-1$
	}

	private void printRtFieldsWithGenericReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int fieldsCount = in.readInt();
		println("Fields count:", fieldsCount); //$NON-NLS-1$
		for (int i = 0; i < fieldsCount; i++) {
			long fieldId = readFieldID(in);
			String name = readString(in);
			String signature = readString(in);
			String genericSignature = readString(in);
			int modifiers = in.readInt();
			printlnFieldId("Field id:", fieldId); //$NON-NLS-1$
			println("Name:", name); //$NON-NLS-1$
			println("Signature:", signature); //$NON-NLS-1$
			println("Generic signature:", genericSignature); //$NON-NLS-1$
			printFieldModifiers(modifiers);
		}
	}

	private void printRtMethodsWithGenericReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int methodsCount = in.readInt();
		println("Methods count:", methodsCount); //$NON-NLS-1$
		for (int i = 0; i < methodsCount; i++) {
			long methodId = readMethodID(in);
			String name = readString(in);
			String signature = readString(in);
			String genericSignature = readString(in);
			int modifiers = in.readInt();
			printlnMethodId("Method id:", methodId); //$NON-NLS-1$
			println("Name:", name); //$NON-NLS-1$
			println("Signature", signature);
			println("Generic signature:", genericSignature); //$NON-NLS-1$
			printMethodModifiers(modifiers);
		}
	}

	private void printCtSuperclassCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classTypeId = readReferenceTypeID(in);
		printlnReferenceTypeId("Class type id:", classTypeId); //$NON-NLS-1$
	}

	private void printCtSuperclassReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long superclassTypeId = readReferenceTypeID(in);
		printlnReferenceTypeId("Superclass type id:", superclassTypeId); //$NON-NLS-1$
	}

	private void printCtSetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classTypeId = readReferenceTypeID(in);
		int fieldsCount = in.readInt();
		printlnReferenceTypeId("Class type id:", classTypeId); //$NON-NLS-1$
		println("Fields count:", fieldsCount); //$NON-NLS-1$
		throw new UnableToParseDataException(
				"List of values: NOT MANAGED", remainderData(in)); //$NON-NLS-1$
	}

	private void printCtInvokeMethodCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classTypeId = readReferenceTypeID(in);
		long threadId = readObjectID(in);
		long methodId = readMethodID(in);
		int argumentsCount = in.readInt();
		printlnReferenceTypeId("Class type id:", classTypeId); //$NON-NLS-1$
		printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
		printlnMethodId("Method id:", methodId); //$NON-NLS-1$
		println("Arguments count:", argumentsCount); //$NON-NLS-1$
		for (int i = 0; i < argumentsCount; i++) {
			readAndPrintlnTaggedValue("Argument:", in); //$NON-NLS-1$
		}
		int invocationOptions = in.readInt();
		printInvocationOptions(invocationOptions);
	}

	private void printCtInvokeMethodReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		readAndPrintlnTaggedValue("Return value:", in); //$NON-NLS-1$
		byte signatureByte = in.readByte();
		long exception = readObjectID(in);
		printlnTaggedObjectId("Exception object id:", exception, signatureByte); //$NON-NLS-1$
	}

	private void printCtNewInstanceCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		printCtInvokeMethodCommand(in);
	}

	private void printCtNewInstanceReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		byte objectSignatureByte = in.readByte();
		long newObjectId = readObjectID(in);
		byte exceptionSignatureByte = in.readByte();
		long exception = readObjectID(in);
		printlnTaggedObjectId(
				"New object id:", newObjectId, objectSignatureByte); //$NON-NLS-1$
		printlnTaggedObjectId(
				"Exception object id:", exception, exceptionSignatureByte); //$NON-NLS-1$
	}

	private void printAtNewInstanceCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long arrayTypeId = readReferenceTypeID(in);
		int length = in.readInt();
		printlnReferenceTypeId("Array type id:", arrayTypeId); //$NON-NLS-1$
		println("Length:", length); //$NON-NLS-1$
	}

	private void printAtNewInstanceReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		byte signatureByte = in.readByte();
		long newArrayId = readObjectID(in);
		printlnTaggedObjectId("New array id:", newArrayId, signatureByte); //$NON-NLS-1$
	}

	private void printMDefaultCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long classTypeId = readReferenceTypeID(in);
		long methodId = readMethodID(in);
		printlnReferenceTypeId("Class type id:", classTypeId); //$NON-NLS-1$
		printlnMethodId("Method id:", methodId); //$NON-NLS-1$
	}

	private void printMLineTableReply(DataInputStream in) throws IOException {
		long start = in.readLong();
		long end = in.readLong();
		int lines = in.readInt();
		println("Lowest valid code index:", start); //$NON-NLS-1$
		println("Highest valid code index:", end); //$NON-NLS-1$
		println("Number of lines:", lines); //$NON-NLS-1$
		for (int i = 0; i < lines; i++) {
			long lineCodeIndex = in.readLong();
			int lineNumber = in.readInt();
			println("Line code Index:", lineCodeIndex); //$NON-NLS-1$
			println("Line number:", lineNumber); //$NON-NLS-1$
		}
	}

	private void printMVariableTableReply(DataInputStream in)
			throws IOException {
		int slotsUsedByArgs = in.readInt();
		int variablesCount = in.readInt();
		println("Nb of slots used by all args:", slotsUsedByArgs); //$NON-NLS-1$
		println("Nb of variables:", variablesCount); //$NON-NLS-1$
		for (int i = 0; i < variablesCount; i++) {
			long codeIndex = in.readLong();
			String name = readString(in);
			String signature = readString(in);
			int length = in.readInt();
			int slotId = in.readInt();
			println("First code index:", codeIndex); //$NON-NLS-1$
			println("Variable name:", name); //$NON-NLS-1$
			println("Variable type signature:", signature); //$NON-NLS-1$
			println("Code index length:", length); //$NON-NLS-1$
			println("Slot id:", slotId); //$NON-NLS-1$
		}
	}

	private void printMBytecodesReply(DataInputStream in) throws IOException {
		int bytes = in.readInt();
		println("Nb of bytes:", bytes); //$NON-NLS-1$
		while ((bytes -= in.skipBytes(bytes)) != 0) {
		}
		printDescription("Method bytes:"); //$NON-NLS-1$
		println("skipped"); //$NON-NLS-1$
	}

	private void printMIsObsoleteReply(DataInputStream in) throws IOException {
		boolean isObsolete = in.readBoolean();
		println("Is obsolete:", isObsolete); //$NON-NLS-1$
	}

	private void printMVariableTableWithGenericReply(DataInputStream in)
			throws IOException {
		int slotsUsedByArgs = in.readInt();
		int variablesCount = in.readInt();
		println("Nb of slots used by all args:", slotsUsedByArgs); //$NON-NLS-1$
		println("Nb of variables:", variablesCount); //$NON-NLS-1$
		for (int i = 0; i < variablesCount; i++) {
			long codeIndex = in.readLong();
			String name = readString(in);
			String signature = readString(in);
			String genericSignature = readString(in);
			int length = in.readInt();
			int slotId = in.readInt();
			println("First code index:", codeIndex); //$NON-NLS-1$
			println("Variable name:", name); //$NON-NLS-1$
			println("Variable type signature:", signature); //$NON-NLS-1$
			println("Var. type generic signature:", genericSignature); //$NON-NLS-1$
			println("Code index length:", length); //$NON-NLS-1$
			println("Slot id:", slotId); //$NON-NLS-1$
		}
	}

	private void printOrDefaultCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long objectId = readObjectID(in);
		println("Object id:", objectId); //$NON-NLS-1$
	}

	private void printOrReferenceTypeReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		byte refTypeTag = in.readByte();
		long typeId = readReferenceTypeID(in);
		printRefTypeTag(refTypeTag);
		printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
	}

	private void printOrGetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long objectId = readObjectID(in);
		int fieldsCount = in.readInt();
		println("Object id:", objectId); //$NON-NLS-1$
		println("Fields count:", fieldsCount); //$NON-NLS-1$
		for (int i = 0; i < fieldsCount; i++) {
			long fieldId = readFieldID(in);
			println("Field id:", fieldId); //$NON-NLS-1$
		}
	}

	private void printOrGetValuesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int valuesCount = in.readInt();
		println("Values count:", valuesCount); //$NON-NLS-1$
		for (int i = 0; i < valuesCount; i++) {
			readAndPrintlnTaggedValue("Value:", in); //$NON-NLS-1$
		}
	}

	private void printOrSetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long objectId = readObjectID(in);
		int fieldsCount = in.readInt();
		println("Object id:", objectId); //$NON-NLS-1$
		println("Fields count:", fieldsCount); //$NON-NLS-1$
		throw new UnableToParseDataException(
				"List of values: NOT MANAGED", remainderData(in)); //$NON-NLS-1$
	}

	private void printOrMonitorInfoReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long ownerThreadId = readObjectID(in);
		int entryCount = in.readInt();
		int waiters = in.readInt();
		printlnObjectId("Owner thread id:", ownerThreadId); //$NON-NLS-1$
		println("Entry count:", entryCount); //$NON-NLS-1$
		println("Nb of waiters:", waiters); //$NON-NLS-1$
		long waiterThreadId;
		for (int i = 0; i < waiters; i++) {
			waiterThreadId = readObjectID(in);
			printlnObjectId("Waiting thread id:", waiterThreadId); //$NON-NLS-1$
		}
	}

	private void printOrInvokeMethodCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long objectId = readObjectID(in);
		long threadId = readObjectID(in);
		long classTypeId = readReferenceTypeID(in);
		long methodId = readMethodID(in);
		int argsCount = in.readInt();
		printlnObjectId("Object id:", objectId); //$NON-NLS-1$
		printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
		printlnReferenceTypeId("Class type id:", classTypeId); //$NON-NLS-1$
		printlnMethodId("Method id:", methodId); //$NON-NLS-1$
		println("Arguments count:", argsCount); //$NON-NLS-1$
		for (int i = 0; i < argsCount; i++) {
			readAndPrintlnTaggedValue("Argument:", in); //$NON-NLS-1$
		}
		int invocationOption = in.readInt();
		printInvocationOptions(invocationOption);
	}

	private void printOrInvokeMethodReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		readAndPrintlnTaggedValue("Return value:", in); //$NON-NLS-1$
		byte signatureByte = in.readByte();
		long exception = readObjectID(in);
		printlnTaggedObjectId("Exception object id:", exception, signatureByte); //$NON-NLS-1$
	}

	private void printOrIsCollectedReply(DataInputStream in) throws IOException {
		boolean isCollected = in.readBoolean();
		println("Is collected:", isCollected); //$NON-NLS-1$
	}

	private void printSrValueCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long stringObjectId = readObjectID(in);
		printlnObjectId("String object id:", stringObjectId); //$NON-NLS-1$
	}

	private void printSrValueReply(DataInputStream in) throws IOException {
		String value = readString(in);
		println("Value:", value); //$NON-NLS-1$
	}

	private void printTrDefaultCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long threadId = readObjectID(in);
		printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
	}

	private void printTrNameReply(DataInputStream in) throws IOException {
		String threadName = readString(in);
		println("Name:", threadName); //$NON-NLS-1$
	}

	private void printTrStatusReply(DataInputStream in) throws IOException {
		int threadStatus = in.readInt();
		int suspendStatus = in.readInt();
		printThreadStatus(threadStatus);
		printSuspendStatus(suspendStatus);
	}

	private void printTrThreadGroupReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long threadGroupId = readObjectID(in);
		printlnObjectId("Thread group id:", threadGroupId); //$NON-NLS-1$
	}

	private void printTrFramesCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long threadId = readObjectID(in);
		int startFrame = in.readInt();
		int length = in.readInt();
		printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
		println("First frame:", startFrame); //$NON-NLS-1$
		println("Number of frame:", length); //$NON-NLS-1$
	}

	private void printTrFramesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int framesCount = in.readInt();
		println("Frames count:", framesCount); //$NON-NLS-1$
		for (int i = 0; i < framesCount; i++) {
			long frameId = readFrameID(in);
			printlnFrameId("Frame id:", frameId); //$NON-NLS-1$
			readAndPrintLocation(in);
		}
	}

	private void printTrFrameCountReply(DataInputStream in) throws IOException {
		int framesCount = in.readInt();
		println("Frames count:", framesCount); //$NON-NLS-1$
	}

	private void printTrOwnedMonitorsReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int monitorsCount = in.readInt();
		println("Monitors count:", monitorsCount); //$NON-NLS-1$
		for (int i = 0; i < monitorsCount; i++) {
			byte signatureByte = in.readByte();
			long monitorObjectId = readObjectID(in);
			printlnTaggedObjectId(
					"Monitor object id:", monitorObjectId, signatureByte); //$NON-NLS-1$
		}
	}

	private void printTrCurrentContendedMonitorReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		byte signatureByte = in.readByte();
		long monitorObjectId = readObjectID(in);
		printlnTaggedObjectId(
				"Monitor object id:", monitorObjectId, signatureByte); //$NON-NLS-1$
	}

	private void printTrStopCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long threadId = readObjectID(in);
		long exceptionObjectId = readObjectID(in);
		printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
		printlnObjectId("Exception object id:", exceptionObjectId); //$NON-NLS-1$
	}

	private void printTrSuspendCountReply(DataInputStream in)
			throws IOException {
		int suspendCount = in.readInt();
		println("Suspend count:", suspendCount); //$NON-NLS-1$
	}

	private void printTgrDefaultCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long threadGroupId = readObjectID(in);
		printlnObjectId("Thread group id:", threadGroupId); //$NON-NLS-1$
	}

	private void printTgrNameReply(DataInputStream in) throws IOException {
		String name = readString(in);
		println("Name:", name); //$NON-NLS-1$
	}

	private void printTgrParentReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long parentThreadGroupId = readObjectID(in);
		printlnObjectId("Parent thread group id:", parentThreadGroupId); //$NON-NLS-1$
	}

	private void printTgrChildrenReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int childThreadsCount = in.readInt();
		println("Child threads count:", childThreadsCount); //$NON-NLS-1$
		for (int i = 0; i < childThreadsCount; i++) {
			long childThreadId = readObjectID(in);
			printlnObjectId("Child thread id:", childThreadId); //$NON-NLS-1$
		}
		int childGroupThreadsCount = in.readInt();
		println("Child group threads count:", childGroupThreadsCount); //$NON-NLS-1$
		for (int i = 0; i < childGroupThreadsCount; i++) {
			long childGroupThreadId = readObjectID(in);
			printlnObjectId("Child group thread id:", childGroupThreadId); //$NON-NLS-1$
		}
	}

	private void printArLengthCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long arrayObjectId = readObjectID(in);
		printlnObjectId("Array object id:", arrayObjectId); //$NON-NLS-1$
	}

	private void printArLengthReply(DataInputStream in) throws IOException {
		int length = in.readInt();
		println("Length:", length); //$NON-NLS-1$
	}

	private void printArGetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long arrayObjectId = readObjectID(in);
		int firstIndex = in.readInt();
		int length = in.readInt();
		printlnObjectId("Array object id:", arrayObjectId); //$NON-NLS-1$
		println("First index:", firstIndex); //$NON-NLS-1$
		println("Length:", length); //$NON-NLS-1$
	}

	private void printArGetValuesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		readAndPrintArrayRegion(in);
	}

	private void printArSetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long arrayObjectId = readObjectID(in);
		int firstIndex = in.readInt();
		int length = in.readInt();
		printlnObjectId("Array object id:", arrayObjectId); //$NON-NLS-1$
		println("First index:", firstIndex); //$NON-NLS-1$
		println("Length:", length); //$NON-NLS-1$
		throw new UnableToParseDataException(
				"List of values: NOT MANAGED", remainderData(in)); //$NON-NLS-1$
	}

	private void printClrVisibleClassesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classLoaderObjectId = readObjectID(in);
		printlnObjectId("Class loader object id:", classLoaderObjectId); //$NON-NLS-1$
	}

	private void printClrVisibleClassesReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		int classesCount = in.readInt();
		println("Classes count:", classesCount); //$NON-NLS-1$
		for (int i = 0; i < classesCount; i++) {
			byte refTypeTag = in.readByte();
			long typeId = readReferenceTypeID(in);
			printRefTypeTag(refTypeTag);
			printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
		}
	}

	private void printErSetCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		byte eventKind = in.readByte();
		byte suspendPolicy = in.readByte();
		int modifiersCount = in.readInt();
		printEventKind(eventKind);
		printSuspendPolicy(suspendPolicy);
		println("Modifiers count:", modifiersCount); //$NON-NLS-1$
		for (int i = 0; i < modifiersCount; i++) {
			byte modKind = in.readByte();
			printDescription("Modifier kind:"); //$NON-NLS-1$
			printHex(modKind);
			switch (modKind) {
			case 1: // count
				println(" (Count)"); //$NON-NLS-1$
				int count = in.readInt();
				println("Count:", count); //$NON-NLS-1$
				break;
			case 2: // conditional
				println(" (Conditional)"); //$NON-NLS-1$
				int exprId = in.readInt();
				println("Expression id:", exprId); //$NON-NLS-1$
				break;
			case 3: // thread only
				println(" (ThreadOnly)"); //$NON-NLS-1$
				long threadId = readObjectID(in);
				printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
				break;
			case 4: // class only
				println(" (ClassOnly)"); //$NON-NLS-1$
				long classId = readReferenceTypeID(in);
				printlnReferenceTypeId("Class type id:", classId); //$NON-NLS-1$
				break;
			case 5: // class match
				println(" (ClassMatch)"); //$NON-NLS-1$
				String classPatern = readString(in);
				println("Class pattern:", classPatern); //$NON-NLS-1$
				break;
			case 6: // class exclude
				println(" (ClassExclude)"); //$NON-NLS-1$
				classPatern = readString(in);
				println("Class pattern:", classPatern); //$NON-NLS-1$
				break;
			case 7: // location only
				println(" (LocationOnly)"); //$NON-NLS-1$
				readAndPrintLocation(in);
				break;
			case 8: // exception only
				println(" (ExceptionOnly)"); //$NON-NLS-1$
				long typeId = readReferenceTypeID(in);
				boolean caught = in.readBoolean();
				boolean uncaught = in.readBoolean();
				printlnReferenceTypeId("Exception type id:", typeId); //$NON-NLS-1$
				println("Caught:", caught); //$NON-NLS-1$
				println("Uncaught:", uncaught); //$NON-NLS-1$
				break;
			case 9: // field only
				println(" (FieldOnly)"); //$NON-NLS-1$
				long declaringTypeId = readReferenceTypeID(in);
				long fieldId = readFieldID(in);
				printlnReferenceTypeId("Declaring type id:", declaringTypeId); //$NON-NLS-1$
				printlnFieldId("Field id:", fieldId); //$NON-NLS-1$
				break;
			case 10: // step
				println(" (Step)"); //$NON-NLS-1$
				threadId = readObjectID(in);
				int stepSize = in.readInt();
				int stepDepth = in.readInt();
				printlnObjectId("Thread id:", threadId); //$NON-NLS-1$
				printStepSize(stepSize);
				printStepDepth(stepDepth);
				break;
			case 11: // instance only
				println(" (InstanceOnly)"); //$NON-NLS-1$
				long objectId = readObjectID(in);
				printlnObjectId("Object id:", objectId); //$NON-NLS-1$
				break;
			}
		}
	}

	private void printErSetReply(DataInputStream in) throws IOException {
		int requestId = in.readInt();
		println("Request id:", requestId); //$NON-NLS-1$
	}

	private void printErClearCommand(DataInputStream in) throws IOException {
		byte eventKind = in.readByte();
		int requestId = in.readInt();
		printEventKind(eventKind);
		println("Request id:", requestId); //$NON-NLS-1$
	}

	private void printSfDefaultCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		long threadId = readObjectID(in);
		long frameId = readFrameID(in);
		printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
		printlnFrameId("Frame id:", frameId); //$NON-NLS-1$
	}

	private void printSfGetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long threadId = readObjectID(in);
		long frameId = readFrameID(in);
		int slotsCount = in.readInt();
		printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
		printlnFrameId("Frame id:", frameId); //$NON-NLS-1$
		println("Slots count:", slotsCount); //$NON-NLS-1$
		for (int i = 0; i < slotsCount; i++) {
			int slotIndex = in.readInt();
			byte signatureTag = in.readByte();
			println("Slot index:", slotIndex); //$NON-NLS-1$
			printDescription("Signature tag:"); //$NON-NLS-1$
			printSignatureByte(signatureTag, true);
			println();
		}
	}

	private void printSfGetValuesReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		int valuesCount = in.readInt();
		println("Values count:", valuesCount); //$NON-NLS-1$
		for (int i = 0; i < valuesCount; i++) {
			readAndPrintlnTaggedValue("Value:", in); //$NON-NLS-1$
		}
	}

	private void printSfSetValuesCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long threadId = readObjectID(in);
		long frameId = readFrameID(in);
		int slotsCount = in.readInt();
		printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
		printlnFrameId("Frame id:", frameId); //$NON-NLS-1$
		println("Slots count:", slotsCount); //$NON-NLS-1$
		for (int i = 0; i < slotsCount; i++) {
			int slotIndex = in.readInt();
			println("Slot index:", slotIndex); //$NON-NLS-1$
			readAndPrintlnTaggedValue("Values:", in); //$NON-NLS-1$
		}
	}

	private void printSfThisObjectReply(DataInputStream in) throws IOException,
			UnableToParseDataException {
		byte signatureByte = in.readByte();
		long objectId = readObjectID(in);
		printlnTaggedObjectId("'this' object id:", objectId, signatureByte); //$NON-NLS-1$
	}

	private void printCorReflectedTypeCommand(DataInputStream in)
			throws IOException, UnableToParseDataException {
		long classObjectId = readObjectID(in);
		printlnObjectId("Class object id:", classObjectId); //$NON-NLS-1$
	}

	private void printCorReflectedTypeReply(DataInputStream in)
			throws IOException, UnableToParseDataException {
		byte refTypeTag = in.readByte();
		long typeId = readReferenceTypeID(in);
		printRefTypeTag(refTypeTag);
		printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
	}

	private void printECompositeCommand(DataInputStream in) throws IOException,
			UnableToParseDataException {
		byte suspendPolicy = in.readByte();
		int eventsCount = in.readInt();
		printSuspendPolicy(suspendPolicy);
		println("Events count:", eventsCount); //$NON-NLS-1$
		for (int i = 0; i < eventsCount; i++) {
			byte eventKind = in.readByte();
			int requestId = in.readInt();
			printEventKind(eventKind);
			println("Request id:", requestId); //$NON-NLS-1$
			switch (eventKind) {
			case EVENTKIND_VM_START:
				long threadId = readObjectID(in);
				printlnObjectId("Initial thread object id:", threadId); //$NON-NLS-1$
				break;
			case EVENTKIND_SINGLE_STEP:
			case EVENTKIND_BREAKPOINT:
			case EVENTKIND_METHOD_ENTRY:
			case EVENTKIND_METHOD_EXIT:
				threadId = readObjectID(in);
				printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
				readAndPrintLocation(in);
				break;
			case EVENTKIND_EXCEPTION:
				threadId = readObjectID(in);
				readAndPrintLocation(in);
				byte signatureByte = in.readByte();
				long objectId = readObjectID(in);
				printlnTaggedObjectId(
						"Exception object id:", objectId, signatureByte); //$NON-NLS-1$
				readAndPrintLocation(in);
				break;
			case EVENTKIND_THREAD_START:
			case EVENTKIND_THREAD_DEATH:
				threadId = readObjectID(in);
				printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
				break;
			case EVENTKIND_CLASS_PREPARE:
				threadId = readObjectID(in);
				byte refTypeTag = in.readByte();
				long typeId = readReferenceTypeID(in);
				String typeSignature = readString(in);
				int status = in.readInt();
				printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
				printRefTypeTag(refTypeTag);
				printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
				println("Type signature:", typeSignature); //$NON-NLS-1$
				println("Status:", status); //$NON-NLS-1$
				break;
			case EVENTKIND_CLASS_UNLOAD:
				typeSignature = readString(in);
				println("Type signature:", typeSignature); //$NON-NLS-1$
				break;
			case EVENTKIND_FIELD_ACCESS:
				threadId = readObjectID(in);
				printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
				readAndPrintLocation(in);
				refTypeTag = in.readByte();
				typeId = readReferenceTypeID(in);
				long fieldId = readFieldID(in);
				signatureByte = in.readByte();
				objectId = readObjectID(in);
				printRefTypeTag(refTypeTag);
				printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
				printlnFieldId("Field id:", fieldId); //$NON-NLS-1$
				printlnTaggedObjectId("Object id:", objectId, signatureByte); //$NON-NLS-1$
				break;
			case EVENTKIND_FIELD_MODIFICATION:
				threadId = readObjectID(in);
				printlnObjectId("Thread object id:", threadId); //$NON-NLS-1$
				readAndPrintLocation(in);
				refTypeTag = in.readByte();
				typeId = readReferenceTypeID(in);
				fieldId = readFieldID(in);
				signatureByte = in.readByte();
				objectId = readObjectID(in);
				printRefTypeTag(refTypeTag);
				printlnReferenceTypeId("Type id:", typeId); //$NON-NLS-1$
				printlnFieldId("Field id:", fieldId); //$NON-NLS-1$
				printlnTaggedObjectId("Object id:", objectId, signatureByte); //$NON-NLS-1$
				readAndPrintlnTaggedValue("Value:", in); //$NON-NLS-1$
				break;
			case EVENTKIND_VM_DEATH:
				break;
			}
		}
	}

	/**
	 * Reads String from Jdwp stream. Read a UTF where length has 4 bytes, and
	 * not just 2. This code was based on the OTI Retysin source for readUTF.
	 */
	private static String readString(DataInputStream in) throws IOException {
		int utfSize = in.readInt();
		byte utfBytes[] = new byte[utfSize];
		in.readFully(utfBytes);
		/* Guess at buffer size */
		StringBuffer strBuffer = new StringBuffer(utfSize / 3 * 2);
		for (int i = 0; i < utfSize;) {
			int a = utfBytes[i] & 0xFF;
			if ((a >> 4) < 12) {
				strBuffer.append((char) a);
				i++;
			} else {
				int b = utfBytes[i + 1] & 0xFF;
				if ((a >> 4) < 14) {
					if ((b & 0xBF) == 0) {
						throw new UTFDataFormatException(
								"Second byte input does not match UTF Specification"); //$NON-NLS-1$
					}
					strBuffer.append((char) (((a & 0x1F) << 6) | (b & 0x3F)));
					i += 2;
				} else {
					int c = utfBytes[i + 2] & 0xFF;
					if ((a & 0xEF) > 0) {
						if (((b & 0xBF) == 0) || ((c & 0xBF) == 0)) {
							throw new UTFDataFormatException(
									"Second or third byte input does not mach UTF Specification_"); //$NON-NLS-1$
						}
						strBuffer.append((char) (((a & 0x0F) << 12)
								| ((b & 0x3F) << 6) | (c & 0x3F)));
						i += 3;
					} else {
						throw new UTFDataFormatException(
								"Input does not match UTF Specification"); //$NON-NLS-1$
					}
				}
			}
		}
		return strBuffer.toString();
	}

	private byte[] remainderData(DataInputStream in) throws IOException {
		byte[] buffer = new byte[100];
		byte[] res = new byte[0], newRes;
		int resLength = 0, length;
		while ((length = in.read(buffer)) != -1) {
			newRes = new byte[resLength + length];
			System.arraycopy(res, 0, newRes, 0, resLength);
			System.arraycopy(buffer, 0, newRes, resLength, length);
			res = newRes;
			resLength += length;
		}
		return res;
	}

	private long readObjectID(DataInputStream in) throws IOException,
			UnableToParseDataException {
		if (!TcpipSpy.hasSizes()) {
			throw new UnableToParseDataException(
					"Unable to parse remaining data", remainderData(in)); //$NON-NLS-1$
		}
		return readID(in, TcpipSpy.getObjectIDSize());
	}

	private long readReferenceTypeID(DataInputStream in) throws IOException,
			UnableToParseDataException {
		if (!TcpipSpy.hasSizes()) {
			throw new UnableToParseDataException(
					"Unable to parse remaining data", remainderData(in)); //$NON-NLS-1$
		}
		return readID(in, TcpipSpy.getReferenceTypeIDSize());
	}

	private long readFieldID(DataInputStream in) throws IOException,
			UnableToParseDataException {
		if (!TcpipSpy.hasSizes()) {
			throw new UnableToParseDataException(
					"Unable to parse remaining data", remainderData(in)); //$NON-NLS-1$
		}
		return readID(in, TcpipSpy.getFieldIDSize());
	}

	private long readMethodID(DataInputStream in) throws IOException,
			UnableToParseDataException {
		if (!TcpipSpy.hasSizes()) {
			throw new UnableToParseDataException(
					"Unable to parse remaining data", remainderData(in)); //$NON-NLS-1$
		}
		return readID(in, TcpipSpy.getMethodIDSize());
	}

	private long readFrameID(DataInputStream in) throws IOException,
			UnableToParseDataException {
		if (!TcpipSpy.hasSizes()) {
			throw new UnableToParseDataException(
					"Unable to parse remaining data", remainderData(in)); //$NON-NLS-1$
		}
		return readID(in, TcpipSpy.getFrameIDSize());
	}

	private long readID(DataInputStream in, int size) throws IOException {
		long id = 0;
		for (int i = 0; i < size; i++) {
			int b = in.readUnsignedByte(); // Note that the byte must be treated
											// as unsigned.
			id = id << 8 | b;
		}
		return id;
	}

	private void readAndPrintlnTaggedValue(String description,
			DataInputStream in) throws IOException, UnableToParseDataException {
		byte tag = in.readByte();
		readAndPrintlnUntaggedValue(description, in, tag, true);
	}

	private void readAndPrintlnUntaggedValue(String description,
			DataInputStream in, byte tag, boolean printTagValue)
			throws IOException, UnableToParseDataException {
		printDescription(description);
		int size;
		boolean isId = false;
		switch (tag) {
		case VOID_TAG:
			printSignatureByte(tag, printTagValue);
			println();
			return;
		case BOOLEAN_TAG:
			if (printTagValue) {
				printSignatureByte(tag, true);
				print(' ');
				println(in.readBoolean());
			} else {
				println(in.readBoolean());
				print(' ');
				printSignatureByte(tag, false);
			}
			return;
		case BYTE_TAG:
			size = 1;
			break;
		case CHAR_TAG:
		case SHORT_TAG:
			size = 2;
			break;
		case INT_TAG:
		case FLOAT_TAG:
			size = 4;
			break;
		case DOUBLE_TAG:
		case LONG_TAG:
			size = 8;
			break;
		case ARRAY_TAG:
		case OBJECT_TAG:
		case STRING_TAG:
		case THREAD_TAG:
		case THREAD_GROUP_TAG:
		case CLASS_LOADER_TAG:
		case CLASS_OBJECT_TAG:
			if (!TcpipSpy.hasSizes()) {
				throw new UnableToParseDataException(
						"Unable to parse remaining data", remainderData(in)); //$NON-NLS-1$
			}
			size = TcpipSpy.getObjectIDSize();
			isId = true;
			break;
		default:
			size = 0;
			break;
		}

		long value = readID(in, size);
		if (printTagValue) {
			printSignatureByte(tag, true);
			print(' ');
		}
		printHex(value, size);
		if (isId) {
			printParanthetical(value);
		} else {
			switch (tag) {
			case BYTE_TAG:
				printParanthetical((byte) value);
				break;
			case CHAR_TAG:
				printParanthetical((char) value);
				break;
			case SHORT_TAG:
				printParanthetical((short) value);
				break;
			case INT_TAG:
				printParanthetical((int) value);
				break;
			case FLOAT_TAG:
				printParanthetical(Float.intBitsToFloat((int) value));
				break;
			case DOUBLE_TAG:
				printParanthetical(Double.longBitsToDouble(value));
				break;
			case LONG_TAG:
				printParanthetical(value);
				break;
			}
		}
		if (!printTagValue) {
			print(' ');
			printSignatureByte(tag, false);
		}
		println();
	}

	private void printSignatureByte(byte signatureByte, boolean printValue) {
		String type;
		switch (signatureByte) {
		case VOID_TAG:
			type = "void"; //$NON-NLS-1$
			break;
		case BOOLEAN_TAG:
			type = "boolean"; //$NON-NLS-1$
			break;
		case BYTE_TAG:
			type = "byte"; //$NON-NLS-1$
			break;
		case CHAR_TAG:
			type = "char"; //$NON-NLS-1$
			break;
		case SHORT_TAG:
			type = "short"; //$NON-NLS-1$
			break;
		case INT_TAG:
			type = "int"; //$NON-NLS-1$
			break;
		case FLOAT_TAG:
			type = "float"; //$NON-NLS-1$
			break;
		case DOUBLE_TAG:
			type = "double"; //$NON-NLS-1$
			break;
		case LONG_TAG:
			type = "long"; //$NON-NLS-1$
			break;
		case ARRAY_TAG:
			type = "array id"; //$NON-NLS-1$
			break;
		case OBJECT_TAG:
			type = "object id"; //$NON-NLS-1$
			break;
		case STRING_TAG:
			type = "string id"; //$NON-NLS-1$
			break;
		case THREAD_TAG:
			type = "thread id"; //$NON-NLS-1$
			break;
		case THREAD_GROUP_TAG:
			type = "thread group id"; //$NON-NLS-1$
			break;
		case CLASS_LOADER_TAG:
			type = "class loader id"; //$NON-NLS-1$
			break;
		case CLASS_OBJECT_TAG:
			type = "class object id"; //$NON-NLS-1$
			break;
		default:
			type = "unknown"; //$NON-NLS-1$
			break;
		}
		if (printValue) {
			printHex(signatureByte);
			print(" ("); //$NON-NLS-1$
			print(signatureByte);
			print(" - "); //$NON-NLS-1$
		} else {
			print(" ("); //$NON-NLS-1$
		}
		print(type + ')');
	}

	private void readAndPrintLocation(DataInputStream in) throws IOException,
			UnableToParseDataException {
		byte typeTag = in.readByte();
		long classId = readReferenceTypeID(in);
		long methodId = readMethodID(in);
		long index = in.readLong();
		printlnReferenceTypeIdWithTypeTag(
				"Location: class id:", classId, typeTag); //$NON-NLS-1$
		printlnMethodId("          method id:", methodId); //$NON-NLS-1$
		println("          index:", index); //$NON-NLS-1$
	}

	private void readAndPrintArrayRegion(DataInputStream in)
			throws IOException, UnableToParseDataException {
		byte signatureByte = in.readByte();
		int valuesCount = in.readInt();
		printDescription("Signature byte:"); //$NON-NLS-1$
		printSignatureByte(signatureByte, true);
		println();
		println("Values count:", valuesCount); //$NON-NLS-1$
		switch (signatureByte) {
		case ARRAY_TAG:
		case OBJECT_TAG:
		case STRING_TAG:
		case THREAD_TAG:
		case THREAD_GROUP_TAG:
		case CLASS_LOADER_TAG:
		case CLASS_OBJECT_TAG:
			for (int i = 0; i < valuesCount; i++) {
				readAndPrintlnTaggedValue("Value", in); //$NON-NLS-1$
			}
			break;
		default:
			for (int i = 0; i < valuesCount; i++) {
				readAndPrintlnUntaggedValue("Value", in, signatureByte, false); //$NON-NLS-1$
			}
			break;
		}
	}

	protected void println(String description, int value) {
		printDescription(description);
		printHex(value);
		printParanthetical(value);
		println();
	}

	protected void println(String description, long value) {
		printDescription(description);
		printHex(value);
		printParanthetical(value);
		println();
	}

	protected void println(String description, String value) {
		printDescription(description);
		print('\"');
		StringBuffer val = new StringBuffer();
		int pos = 0, lastPos = 0;
		while ((pos = value.indexOf('\n', lastPos)) != -1) {
			pos++;
			val.append(value.substring(lastPos, pos));
			val.append(shift);
			lastPos = pos;
		}
		val.append(value.substring(lastPos, value.length()));
		print(val);
		println('"');
	}

	protected void println(String description, boolean value) {
		printDescription(description);
		println(value);
	}

	protected void printlnReferenceTypeId(String description, long value) {
		println(description, value, TcpipSpy.getReferenceTypeIDSize());
	}

	protected void printlnReferenceTypeIdWithTypeTag(String description,
			long value, byte typeTag) {
		printDescription(description);
		printRefTypeTagValue(typeTag);
		print(" - "); //$NON-NLS-1$
		printHex(value, TcpipSpy.getReferenceTypeIDSize());
		printParanthetical(value);
		println();
	}

	protected void printlnObjectId(String description, long value) {
		printDescription(description);
		printHex(value, TcpipSpy.getObjectIDSize());
		if (value == 0) {
			println(" (NULL)"); //$NON-NLS-1$
		} else {
			printParanthetical(value);
			println();
		}
	}

	protected void printlnTaggedObjectId(String description, long value,
			byte signatureByte) {
		printDescription(description);
		printSignatureByte(signatureByte, true);
		print(' ');
		printHex(value, TcpipSpy.getReferenceTypeIDSize());
		if (value == 0) {
			println(" (NULL)"); //$NON-NLS-1$
		} else {
			printParanthetical(value);
			println();
		}
	}

	protected void printlnFieldId(String description, long value) {
		println(description, value, TcpipSpy.getFieldIDSize());
	}

	protected void printlnMethodId(String description, long value) {
		println(description, value, TcpipSpy.getMethodIDSize());
	}

	protected void printlnFrameId(String description, long value) {
		println(description, value, TcpipSpy.getFrameIDSize());
	}

	protected void println(String description, long value, int size) {
		printDescription(description);
		printHex(value, size);
		printParanthetical(value);
		println();
	}

	protected void printDescription(String description) {
		// current max length = 36 (+2 pad)
		int width = 38 - description.length();
		print(description);
		write(padding, 0, width);
	}

	protected void printHexString(String hex, int width) {
		width -= hex.length();
		print("0x"); //$NON-NLS-1$
		write(zeros, 0, width);
		print(hex);
	}

	protected void printHex(long l, int byteNumber) {
		printHexString(Long.toHexString(l).toUpperCase(), byteNumber * 2);
	}

	protected void printHex(byte b) {
		printHexString(Integer.toHexString(b & 0xFF).toUpperCase(), 2);
	}

	protected void printHex(int i) {
		printHexString(Integer.toHexString(i).toUpperCase(), 8);
	}

	protected void printHex(long l) {
		printHexString(Long.toHexString(l).toUpperCase(), 16);
	}

	protected void printHex(byte[] b) {
		if (b == null) {
			println("NULL"); //$NON-NLS-1$
			return;
		}
		int i, length;
		for (i = 0, length = b.length; i < length; i++) {
			String hexa = Integer.toHexString(b[i]).toUpperCase();
			if (hexa.length() == 1) {
				print('0');
			}
			print(hexa);
			if ((i % 32) == 0 && i != 0) {
				println();
				print(shift);
			} else {
				print(' ');
			}
		}
		println();
	}

	protected void printParanthetical(byte i) {
		print(" ("); //$NON-NLS-1$
		print(i);
		print(')');
	}

	protected void printParanthetical(char i) {
		print(" ("); //$NON-NLS-1$
		print(i);
		print(')');
	}

	protected void printParanthetical(short i) {
		print(" ("); //$NON-NLS-1$
		print(i);
		print(')');
	}

	protected void printParanthetical(int i) {
		print(" ("); //$NON-NLS-1$
		print(i);
		print(')');
	}

	protected void printParanthetical(long l) {
		print(" ("); //$NON-NLS-1$
		print(l);
		print(')');
	}

	protected void printParanthetical(float f) {
		print(" ("); //$NON-NLS-1$
		print(f);
		print(')');
	}

	protected void printParanthetical(double d) {
		print(" ("); //$NON-NLS-1$
		print(d);
		print(')');
	}

	protected void printParanthetical(String s) {
		print(" ("); //$NON-NLS-1$
		print(s);
		print(')');
	}

}
