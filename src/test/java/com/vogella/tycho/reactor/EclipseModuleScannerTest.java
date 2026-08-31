package com.vogella.tycho.reactor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class EclipseModuleScannerTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private final EclipseModuleScanner scanner = new EclipseModuleScanner();

	@Test
	public void findsTheEclipseModuleKinds() throws Exception {
		Path root = folder.getRoot().toPath();
		bundle(root.resolve("bundle"), "com.example.bundle");
		file(root.resolve("feature/feature.xml"), "<feature/>");
		file(root.resolve("site/category.xml"), "<site/>");
		file(root.resolve("oldsite/site.xml"), "<site/>");
		file(root.resolve("product/example.product"), "<product/>");
		file(root.resolve("plain/pom.xml"), "<project/>");

		assertEquals(List.of("bundle", "feature", "oldsite", "plain", "product", "site"), scan(root));
	}

	@Test
	public void findsModulesInSubFolders() throws Exception {
		Path root = folder.getRoot().toPath();
		bundle(root.resolve("bundles/com.example.core"), "com.example.core");
		bundle(root.resolve("tests/com.example.core.tests"), "com.example.core.tests");

		assertEquals(List.of("bundles/com.example.core", "tests/com.example.core.tests"), scan(root));
	}

	@Test
	public void doesNotDescendIntoAModule() throws Exception {
		Path root = folder.getRoot().toPath();
		bundle(root.resolve("bundle"), "com.example.bundle");
		bundle(root.resolve("bundle/nested"), "com.example.nested");

		assertEquals(List.of("bundle"), scan(root));
	}

	@Test
	public void skipsOutputAndHiddenFolders() throws Exception {
		Path root = folder.getRoot().toPath();
		bundle(root.resolve("target/com.example.copy"), "com.example.copy");
		bundle(root.resolve("bin/com.example.copy"), "com.example.copy");
		bundle(root.resolve(".git/com.example.copy"), "com.example.copy");
		bundle(root.resolve(".settings/com.example.copy"), "com.example.copy");

		assertEquals(List.of(), scan(root));
	}

	@Test
	public void ignoresAManifestWithoutSymbolicName() throws Exception {
		Path root = folder.getRoot().toPath();
		file(root.resolve("plain/META-INF/MANIFEST.MF"), "Manifest-Version: 1.0\n\n");

		assertEquals(List.of(), scan(root));
	}

	@Test
	public void appliesExcludes() throws Exception {
		Path root = folder.getRoot().toPath();
		bundle(root.resolve("bundles/com.example.core"), "com.example.core");
		bundle(root.resolve("bundles/com.example.ui"), "com.example.ui");

		assertEquals(List.of("bundles/com.example.core"),
				scanner.scan(root, Set.of("bundles/com.example.ui")));
	}

	@Test
	public void parsesExcludes() {
		assertEquals(Set.of(), EclipseModuleScanner.parseExcludes(null));
		assertEquals(Set.of(), EclipseModuleScanner.parseExcludes("  "));
		assertEquals(Set.of("a", "b/c"), EclipseModuleScanner.parseExcludes(" a , b/c ,"));
	}

	@Test
	public void recognizesASingleFolder() throws Exception {
		Path root = folder.getRoot().toPath();
		bundle(root.resolve("bundle"), "com.example.bundle");
		file(root.resolve("plain/readme.txt"), "");

		assertTrue(scanner.isModule(root.resolve("bundle")));
		assertFalse(scanner.isModule(root.resolve("plain")));
	}

	private List<String> scan(Path root) throws ModuleScanException {
		return scanner.scan(root, Set.of());
	}

	private static void bundle(Path directory, String symbolicName) throws IOException {
		file(directory.resolve("META-INF/MANIFEST.MF"), "Manifest-Version: 1.0\nBundle-ManifestVersion: 2\n"
				+ "Bundle-SymbolicName: " + symbolicName + ";singleton:=true\nBundle-Version: 1.0.0.qualifier\n\n");
	}

	private static void file(Path file, String content) throws IOException {
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}
}
