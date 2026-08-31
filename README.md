# tycho-build-project-reactor

A Maven core extension that discovers the Eclipse modules of a repository and feeds them into the Tycho reactor.
No module list has to be maintained by hand, and no wrapper script or code generation step runs before the build.
`mvn verify` on a fresh clone is enough.

## What it does

The extension hooks `AbstractMavenLifecycleParticipant#afterSessionStart`, which runs after all core extensions are loaded and before Maven reads the reactor projects.
It walks the multi module project directory, classifies every folder, and writes the result to `build/pom.tycho`, which tycho-build turns into an aggregator.

A folder is a module when it contains any of:

| Marker | Kind |
| --- | --- |
| `META-INF/MANIFEST.MF` with a `Bundle-SymbolicName` | plug-in, fragment, test fragment |
| `feature.xml` | feature |
| `category.xml` or `site.xml` | update site |
| `*.product` | product |
| `pom.xml` | anything that brings its own POM |

Recursion stops at a recognised module, so a bundle that contains nested folders is added once.
`target`, `bin`, `node_modules` and every hidden folder (`.git`, `.settings`, ...) are skipped.
Modules are emitted in filesystem order because Tycho sorts the reactor from the OSGi dependencies.

## Using it

The extension cannot be built by the reactor that uses it, so install it first:

```bash
git clone https://github.com/vogellacompany/tycho-build-project-reactor
mvn -f tycho-build-project-reactor install
```

Then declare it in `.mvn/extensions.xml` of the consuming repository, next to tycho-build:

```xml
<extensions>
	<extension>
		<groupId>org.eclipse.tycho</groupId>
		<artifactId>tycho-build</artifactId>
		<version>${tycho.version}</version>
	</extension>
	<extension>
		<groupId>com.vogella.tycho</groupId>
		<artifactId>tycho-build-project-reactor</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</extension>
</extensions>
```

The root `pom.xml` keeps exactly one module entry, the folder the list is generated into:

```xml
<modules>
	<module>build</module>
</modules>
```

The folder itself does not have to exist in git, the extension creates it.
Add `build/` to `.gitignore`.

`examples/minimal-reactor` is a runnable example: `mvn validate` in that folder builds a reactor of four projects out of a root POM that names a single module.
It also shows the version indirection described below.

### Options

| Property | Default | Meaning |
| --- | --- | --- |
| `tycho.reactor.aggregator` | `build` | Folder the module list is generated into. `.` writes `pom.tycho` next to the root `pom.xml`, which needs a Tycho that reads a module list beside a POM. |
| `tycho.reactor.excludes` | empty | Comma separated module paths to keep out of the reactor, for example `com.example.product,tests/com.example.flaky.tests`. |

### Version bumps

`.mvn/extensions.xml` is read before the POMs exist, so it cannot use a POM property.
It does interpolate properties that are set on the command line, which is what `.mvn/maven.config` provides.
Keep the version in one place by writing `-Dreactor.version=1.0.0` into `.mvn/maven.config` and referencing `${reactor.version}` in `.mvn/extensions.xml`.
Otherwise every version bump is an edit of `.mvn/extensions.xml` in every consuming repository.

## Errors

Everything fails before the build starts, so the messages name the folder and say what to do.

```
[ERROR] The reactor of /home/me/git/demo is generated into build/pom.tycho, but /home/me/git/demo/pom.xml
        does not declare <module>build</module>. Add that single module entry, or point
        tycho.reactor.aggregator at the folder that is declared.
```

## Design notes

### Why the module list is a file and not a model contribution

Tycho's pomless support is built on the polyglot `Mapping` and `ModelReader` interfaces, and `TychoModulesMapping` in tycho-build shows that a mapping can add modules to a `pom.xml` without writing anything to disk.
That route is not available to a separate core extension.
Maven gives every entry of `.mvn/extensions.xml` its own class realm (`coreExtension>groupId:artifactId:version`, see `BootstrapCoreExtensionManager`), and the realms do not see each other:

* with `polyglot-common` as a `provided` dependency the mapping class cannot be loaded, and Sisu skips it without a message: the build simply ignores the extension.
* with `polyglot-common` bundled into the extension, the mapping is registered, but the extension now also carries its own `TeslaModelProcessor` and `PolyglotModelManager`. One of the two copies wins the `default` role, and if it is the one from this extension, every pomless module in the build disappears with `Child module .../b1/pom.xml of .../pom.xml does not exist`.

Both were reproduced, which is why the extension depends on `maven-core` only and hands the module list over as a `pom.tycho` file that tycho-build already knows how to read.
Unlike Tycho's own automatic detection the file is not deleted on JVM exit, because the IDE needs it (see below).
It also does not start with Tycho's `## tycho automatic module detection` marker, so Tycho treats it as a hand written list and does not overwrite it.

### The minimal committed anchor

A folder's `pom.xml` always wins over its `pom.tycho`, so the root POM cannot source its own module list, and `afterSessionStart` has no API to add modules to the session.
What has to be in git is therefore the root `pom.xml` with a single `<module>build</module>`.
Nothing else: the `build` folder and the module list in it are created by the extension.

A Tycho that reads a module list next to a `pom.xml` removes even that entry, and the extension supports it with `-Dtycho.reactor.aggregator=.`.
Tycho 5.0.4 is not such a version.

### Eclipse IDE

m2e loads core extensions from `.mvn/extensions.xml`, but it only ever calls `afterProjectsRead`, never `afterSessionStart` (`ProjectRegistryManager`).
Discovery therefore does not run while the IDE reads the projects, and the IDE sees the module list that the last command line build left behind.

In practice: run `mvn validate` once after cloning, then import.
As a fallback the extension also refreshes the list from `afterProjectsRead` when discovery did not already run at session start, so a project that was imported before a new bundle appeared should pick it up on the second *Maven > Update Project*.
That fallback is written against the m2e code path but has not been verified in a running IDE, unlike everything else here.
An import into a fresh clone that never saw a build reports the missing `build` module and needs one `mvn validate` plus an update.

## Building

```bash
mvn clean install
```

The `@Named @Singleton` components are only visible to Maven through the Sisu index that `sisu-maven-plugin:main-index` writes, so the jar must contain `META-INF/sisu/javax.inject.Named`:

```bash
unzip -p target/tycho-build-project-reactor-1.0.0-SNAPSHOT.jar META-INF/sisu/javax.inject.Named
```

## License

EPL-2.0
