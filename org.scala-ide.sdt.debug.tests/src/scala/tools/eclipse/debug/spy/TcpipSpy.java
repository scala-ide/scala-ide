/*******************************************************************************
 * Copyright (c) 2000, 2011 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package scala.tools.eclipse.debug.spy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

import com.ibm.icu.text.MessageFormat;

/**
 * This class can be used to spy all JDWP packets. It should be configured 'in
 * between' the debugger application and the VM (or J9 debug proxy). Its
 * parameters are: 1) The port number to which the debugger application
 * connects; 2) The name of the host on which the VM or proxy waits for a JDWP
 * connection; 3) The port number on which the VM or proxy waits for a JDWP
 * connection; 4) The file where the trace is written to.
 * 
 * Note that if this program is used for tracing JDWP activity of Leapfrog, the
 * 'debug remote program' option must be used, and the J9 proxy must first be
 * started up by hand on the port to which Leapfrog will connect. The J9 proxy
 * that is started up by Leapfrog is not used and will return immediately.
 */
public class TcpipSpy extends Thread {

	private static final byte[] handshakeBytes = "JDWP-Handshake".getBytes(); //$NON-NLS-1$
	private boolean fVMtoDebugger;
	private DataInputStream fDataIn;
	private DataOutputStream fDataOut;

	private static VerbosePacketStream out = new VerbosePacketStream(System.out);
	private static Map<Integer, JdwpConversation> fPackets = new HashMap<Integer, JdwpConversation>();

	private static int fFieldIDSize;
	private static int fMethodIDSize;
	private static int fObjectIDSize;
	private static int fReferenceTypeIDSize;
	private static int fFrameIDSize;
	private static boolean fHasSizes;

	public TcpipSpy(boolean VMtoDebugger, InputStream in, OutputStream out) {
		fVMtoDebugger = VMtoDebugger;
		fDataIn = new DataInputStream(new BufferedInputStream(in));
		fDataOut = new DataOutputStream(new BufferedOutputStream(out));
		fHasSizes = false;
	}

	public static void main(String[] args) {
		int inPort = 0;
		String serverHost = null;
		int outPort = 0;
		String outputFile = null;
		try {
			inPort = Integer.parseInt(args[0]);
			serverHost = args[1];
			outPort = Integer.parseInt(args[2]);
			if (args.length > 3) {
				outputFile = args[3];
			}
		} catch (Exception e) {
			out.println("usage: TcpipSpy <client port> <server host> <server port> [<output file>]"); //$NON-NLS-1$
			System.exit(-1);
		}

		if (outputFile != null) {
			File file = new File(outputFile);
			out.println(MessageFormat
					.format("Writing output to {0}", new Object[] { file.getAbsolutePath() })); //$NON-NLS-1$
			try {
				out = new VerbosePacketStream(new BufferedOutputStream(
						new FileOutputStream(file)));
			} catch (FileNotFoundException e) {
				out.println(MessageFormat
						.format("Could not open {0}.  Using stdout instead", new Object[] { file.getAbsolutePath() })); //$NON-NLS-1$
			}
		}
		out.println();
		try {
			ServerSocket serverSock = new ServerSocket(inPort);
			Socket inSock = serverSock.accept();
			Socket outSock = new Socket(InetAddress.getByName(serverHost),
					outPort);
			new TcpipSpy(false, inSock.getInputStream(),
					outSock.getOutputStream()).start();
			new TcpipSpy(true, outSock.getInputStream(),
					inSock.getOutputStream()).start();
		} catch (Exception e) {
			out.println(e);
		}
	}

	@Override
	public void run() {
		try {
			// Skip handshake.
			int handshakeLength;

			handshakeLength = handshakeBytes.length;
			while (handshakeLength-- > 0) {
				int b = fDataIn.read();
				fDataOut.write(b);
			}
			fDataOut.flush();

			// Print all packages.
			while (true) {
				JdwpPacket p = JdwpPacket.read(fDataIn);
				// we need to store conversation only for command send by the
				// debugger,
				// as there is no answer from the debugger to VM commands.
				if (!(fVMtoDebugger && (p.getFlags() & JdwpPacket.FLAG_REPLY_PACKET) == 0)) {
					store(p);
				}
				out.print(p, fVMtoDebugger);
				out.flush();
				p.write(fDataOut);
				fDataOut.flush();
			}
		} catch (EOFException e) {
		} catch (SocketException e) {
		} catch (IOException e) {
			out.println(MessageFormat.format(
					"Caught exception: {0}", new Object[] { e.toString() })); //$NON-NLS-1$
			e.printStackTrace(out);
		} finally {
			try {
				fDataIn.close();
				fDataOut.close();
			} catch (IOException e) {
			}
			out.flush();
		}
	}

	public static JdwpCommandPacket getCommand(int id) {
		JdwpConversation conversation = fPackets
				.get(new Integer(id));
		if (conversation != null)
			return conversation.getCommand();
		return null;
	}

	protected static void store(JdwpPacket packet) {
		int id = packet.getId();
		JdwpConversation conversation = fPackets
				.get(new Integer(id));
		if (conversation == null) {
			conversation = new JdwpConversation(id);
			fPackets.put(new Integer(id), conversation);
		}

		if ((packet.getFlags() & JdwpPacket.FLAG_REPLY_PACKET) != 0) {
			conversation.setReply((JdwpReplyPacket) packet);
		} else {
			conversation.setCommand((JdwpCommandPacket) packet);
		}
	}

	public static int getCommand(JdwpPacket packet)
			throws UnableToParseDataException {
		JdwpCommandPacket command = null;
		if (packet instanceof JdwpCommandPacket) {
			command = (JdwpCommandPacket) packet;
		} else {
			command = getCommand(packet.getId());
			if (command == null) {
				throw new UnableToParseDataException(
						"This packet is marked as reply, but there is no command with the same id.", null); //$NON-NLS-1$
			}
		}
		return command.getCommand();
	}

	public static boolean hasSizes() {
		return fHasSizes;
	}

	public static void setHasSizes(boolean value) {
		fHasSizes = value;
	}

	public static void setFieldIDSize(int fieldIDSize) {
		fFieldIDSize = fieldIDSize;
	}

	public static int getFieldIDSize() {
		return fFieldIDSize;
	}

	public static void setMethodIDSize(int methodIDSize) {
		fMethodIDSize = methodIDSize;
	}

	public static int getMethodIDSize() {
		return fMethodIDSize;
	}

	public static void setObjectIDSize(int objectIDSize) {
		fObjectIDSize = objectIDSize;
	}

	public static int getObjectIDSize() {
		return fObjectIDSize;
	}

	public static void setReferenceTypeIDSize(int referenceTypeIDSize) {
		fReferenceTypeIDSize = referenceTypeIDSize;
	}

	public static int getReferenceTypeIDSize() {
		return fReferenceTypeIDSize;
	}

	public static void setFrameIDSize(int frameIDSize) {
		fFrameIDSize = frameIDSize;
	}

	public static int getFrameIDSize() {
		return fFrameIDSize;
	}
}
