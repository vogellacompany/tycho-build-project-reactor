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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.Manifest;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Finds the Eclipse modules below a directory and returns them as Maven module paths.
 */
@Named
@Singleton
public class EclipseModuleScanner {

	/** Comma separated list of module paths that are found but must not enter the reactor. */
	public static final String EXCLUDES_PROPERTY = "tycho.reactor.excludes";

	private static final Set<String> IGNORED_FOLDERS = Set.of("target", "bin", "node_modules");

	/**
	 * Returns the modules below {@code root}, as slash separated paths relative to it, in
	 * filesystem order. Tycho sorts the reactor by the OSGi dependencies, so the order is
	 * irrelevant for the build.
	 *
	 * @param excludes module paths to leave out, as returned by this method
	 */
	public List<String> scan(Path root, Collection<String> excludes) throws ModuleScanException {
		Set<String> modules = new LinkedHashSet<>();
		collect(root, root, modules);
		modules.removeAll(excludes);
		return List.copyOf(modules);
	}

	private void collect(Path root, Path directory, Set<String> modules) throws ModuleScanException {
		// a TreeSet keeps the result stable across file systems, which makes builds comparable
		Set<Path> children = new TreeSet<>();
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, Files::isDirectory)) {
			stream.forEach(children::add);
		} catch (IOException e) {
			throw new ModuleScanException("Cannot read the folder " + directory + " while looking for Eclipse modules",
					e);
		}
		for (Path child : children) {
			String name = child.getFileName().toString();
			if (name.startsWith(".") || IGNORED_FOLDERS.contains(name)) {
				continue;
			}
			if (isModule(child)) {
				modules.add(relative(root, child));
			} else {
				collect(root, child, modules);
			}
		}
	}

	/**
	 * Returns whether the folder is something Tycho can build, either because it has a
	 * {@code pom.xml} of its own or because tycho-build can derive one from the Eclipse metadata.
	 */
	public boolean isModule(Path directory) throws ModuleScanException {
		return Files.isRegularFile(directory.resolve("pom.xml")) //
				|| isBundle(directory) //
				|| Files.isRegularFile(directory.resolve("feature.xml")) //
				|| Files.isRegularFile(directory.resolve("category.xml")) //
				|| Files.isRegularFile(directory.resolve("site.xml")) //
				|| hasProduct(directory);
	}

	private boolean isBundle(Path directory) throws ModuleScanException {
		Path manifest = directory.resolve("META-INF/MANIFEST.MF");
		if (!Files.isRegularFile(manifest)) {
			return false;
		}
		try (InputStream in = Files.newInputStream(manifest)) {
			return new Manifest(in).getMainAttributes().getValue("Bundle-SymbolicName") != null;
		} catch (IOException e) {
			throw new ModuleScanException("Cannot read " + manifest
					+ ", the folder looks like a bundle but its manifest is not readable", e);
		}
	}

	private boolean hasProduct(Path directory) throws ModuleScanException {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.product")) {
			return stream.iterator().hasNext();
		} catch (IOException e) {
			throw new ModuleScanException("Cannot read the folder " + directory + " while looking for a product file",
					e);
		}
	}

	private static String relative(Path root, Path module) {
		List<String> segments = new ArrayList<>();
		root.relativize(module).forEach(segment -> segments.add(segment.toString()));
		return String.join("/", segments);
	}

	/**
	 * Parses the value of {@value #EXCLUDES_PROPERTY}.
	 */
	public static Set<String> parseExcludes(String value) {
		if (value == null || value.isBlank()) {
			return Set.of();
		}
		return new LinkedHashSet<>(Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty())
				.toList());
	}
}
