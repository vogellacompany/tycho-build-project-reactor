/*******************************************************************************
 * Copyright (c) 2026 vogella GmbH and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package com.vogella.tycho.reactor;

/**
 * Signals that the module scan hit a folder it could not read.
 */
public class ModuleScanException extends Exception {

	private static final long serialVersionUID = 1L;

	public ModuleScanException(String message, Throwable cause) {
		super(message, cause);
	}
}
