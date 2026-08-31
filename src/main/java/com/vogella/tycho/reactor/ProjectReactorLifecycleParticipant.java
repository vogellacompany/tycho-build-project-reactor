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

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;

/**
 * Discovers the Eclipse modules below the multi module project directory and hands them to Tycho
 * before Maven reads the reactor.
 */
@Named("tycho-project-reactor")
@Singleton
public class ProjectReactorLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	@Inject
	protected Logger logger;

	@Inject
	protected ReactorModules reactorModules;

	@Inject
	protected ModuleListWriter writer;

	private final Set<Path> handledAtSessionStart = ConcurrentHashMap.newKeySet();

	@Override
	public void afterSessionStart(MavenSession session) throws MavenExecutionException {
		MavenExecutionRequest request = session.getRequest();
		File directory = request.getMultiModuleProjectDirectory();
		if (directory == null) {
			return;
		}
		Path root = directory.toPath().toAbsolutePath().normalize();
		if (!ReactorModules.isReactorRoot(root)) {
			return;
		}
		String aggregator = property(request, ModuleListWriter.AGGREGATOR_PROPERTY, ModuleListWriter.DEFAULT_AGGREGATOR);
		Set<String> excludes = new LinkedHashSet<>(
				EclipseModuleScanner.parseExcludes(property(request, EclipseModuleScanner.EXCLUDES_PROPERTY, null)));
		excludes.add(aggregator);
		List<String> modules;
		try {
			modules = reactorModules.get(root, excludes);
		} catch (ModuleScanException e) {
			throw new MavenExecutionException(e.getMessage(), root.toFile());
		}
		if (modules.isEmpty()) {
			logger.warn("tycho-build-project-reactor found no Eclipse module below " + root
					+ ", the reactor stays as the pom.xml files declare it");
			return;
		}
		verifyAnchor(root, aggregator);
		Path file;
		try {
			file = writer.write(root, aggregator, modules);
		} catch (IOException e) {
			throw new MavenExecutionException("Cannot write the module list to " + root.resolve(aggregator)
					+ ", the folder must be writable because the reactor is generated into it: " + e.getMessage(),
					root.toFile());
		}
		handledAtSessionStart.add(root);
		logger.info("Discovered " + modules.size() + " Eclipse modules below " + root + ", listed in "
				+ root.relativize(file));
		modules.forEach(module -> logger.debug("  " + module));
	}

	/**
	 * Refreshes the module list for the next read, which is what m2e needs because it never calls
	 * {@link #afterSessionStart(MavenSession)}.
	 */
	@Override
	public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
		for (MavenProject project : session.getAllProjects() == null ? List.<MavenProject> of()
				: session.getAllProjects()) {
			File basedir = project.getBasedir();
			if (basedir == null) {
				continue;
			}
			Path root = basedir.toPath().toAbsolutePath().normalize();
			if (!ReactorModules.isReactorRoot(root) || handledAtSessionStart.contains(root)) {
				continue; // a command line build has already done the work at session start
			}
			String aggregator = System.getProperty(ModuleListWriter.AGGREGATOR_PROPERTY,
					ModuleListWriter.DEFAULT_AGGREGATOR);
			try {
				Set<String> excludes = new LinkedHashSet<>(
						EclipseModuleScanner.parseExcludes(System.getProperty(EclipseModuleScanner.EXCLUDES_PROPERTY)));
				excludes.add(aggregator);
				writer.write(root, aggregator, reactorModules.rescan(root, excludes));
			} catch (ModuleScanException | IOException e) {
				logger.warn("Cannot refresh the module list of " + root + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Fails the build if the root {@code pom.xml} does not point at the generated aggregator, which
	 * is the one entry that cannot be generated.
	 */
	private void verifyAnchor(Path root, String aggregator) throws MavenExecutionException {
		if (".".equals(aggregator)) {
			return; // the module list sits next to the root pom.xml, no module entry is involved
		}
		Path pom = root.resolve("pom.xml");
		if (!Files.isRegularFile(pom)) {
			throw new MavenExecutionException("There is no pom.xml in " + root
					+ ", tycho-build-project-reactor needs a root pom.xml that declares <module>" + aggregator
					+ "</module>", root.toFile());
		}
		Model model;
		try (Reader reader = Files.newBufferedReader(pom, StandardCharsets.UTF_8)) {
			model = new MavenXpp3Reader().read(reader);
		} catch (Exception e) {
			throw new MavenExecutionException("Cannot read " + pom + ": " + e.getMessage(), pom.toFile());
		}
		if (!model.getModules().contains(aggregator)) {
			throw new MavenExecutionException("The reactor of " + root + " is generated into " + aggregator + "/"
					+ ModuleListWriter.MODULE_LIST + ", but " + pom + " does not declare <module>" + aggregator
					+ "</module>. Add that single module entry, or point " + ModuleListWriter.AGGREGATOR_PROPERTY
					+ " at the folder that is declared.", pom.toFile());
		}
	}

	private static String property(MavenExecutionRequest request, String key, String fallback) {
		Properties user = request.getUserProperties();
		if (user != null && user.getProperty(key) != null) {
			return user.getProperty(key);
		}
		Properties system = request.getSystemProperties();
		if (system != null && system.getProperty(key) != null) {
			return system.getProperty(key);
		}
		return fallback;
	}
}
