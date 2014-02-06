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

/**
 * Exception throws when the spy have not enough information form correctly
 * parse the data.
 */
public class UnableToParseDataException extends Exception {

	/**
	 * All serializable objects should have a stable serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	private byte[] fRemainingData;

	public UnableToParseDataException(String message, byte[] remainingData) {
		super(message);
		fRemainingData = remainingData;
	}

	public byte[] getRemainingData() {
		return fRemainingData;
	}

}
