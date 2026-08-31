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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Holds the discovered modules per reactor root, so that the scan happens once per build.
 */
@Named
@Singleton
public class ReactorModules {

	private static final String EXTENSIONS_FILE = ".mvn/extensions.xml";

	private final Map<Path, List<String>> modulesByRoot = new ConcurrentHashMap<>();

	@Inject
	protected EclipseModuleScanner scanner;

	/**
	 * Returns the modules of the given root, scanning it if that did not happen yet.
	 */
	public List<String> get(Path root, Set<String> excludes) throws ModuleScanException {
		List<String> modules = modulesByRoot.get(root);
		if (modules == null) {
			modules = scanner.scan(root, excludes);
			modulesByRoot.put(root, modules);
		}
		return modules;
	}

	/**
	 * Scans again, discarding what a previous scan of the same root found.
	 */
	public List<String> rescan(Path root, Set<String> excludes) throws ModuleScanException {
		List<String> modules = scanner.scan(root, excludes);
		modulesByRoot.put(root, modules);
		return modules;
	}

	/**
	 * Returns whether the directory is the root of a build that uses core extensions, which is the
	 * only place where a module list may be contributed.
	 */
	public static boolean isReactorRoot(Path directory) {
		return directory != null && Files.isRegularFile(directory.resolve(EXTENSIONS_FILE));
	}
}
