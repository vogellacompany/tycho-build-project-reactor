# Releasing to Maven Central

The extension has to be resolvable before the first build of a consuming repository runs, and core extensions are resolved from `settings.xml` repositories plus Maven Central only.
Maven Central is therefore the distribution channel.

## One time setup

### 1. Claim the namespace

Sign in at <https://central.sonatype.com> and add the namespace `com.vogella` under *Publish > Namespaces*.
The Portal shows a verification code.
Add it to the DNS zone of `vogella.com` as a TXT record on the apex:

```
vogella.com.  IN  TXT  "<code from the portal>"
```

Press *Verify Namespace* once the record resolves (`dig +short TXT vogella.com`).
The namespace covers `com.vogella` and everything below it, so `com.vogella.tycho` needs no separate claim.

### 2. Store the publishing token

*View Account > Generate User Token* produces a user name and password.
Put them into `~/.m2/settings.xml` under the id the POM refers to:

```xml
<settings>
	<servers>
		<server>
			<id>central</id>
			<username>token-user</username>
			<password>token-password</password>
		</server>
	</servers>
</settings>
```

### 3. Publish the signing key

Central verifies the signature against a public key server, so the key that signs the artifacts has to be uploaded once.
The existing vogella GmbH key can be used for this, the same key that signs anything else.

```bash
gpg --keyserver keys.openpgp.org --send-keys 4268D739E67F5C96C319E0A9E116269EA1FCBC1C
gpg --keyserver keyserver.ubuntu.com --send-keys 4268D739E67F5C96C319E0A9E116269EA1FCBC1C
```

`keys.openpgp.org` only serves the user id after the address in it has been confirmed by mail, so check with
`curl -sI https://keys.openpgp.org/vks/v1/by-fingerprint/4268D739E67F5C96C319E0A9E116269EA1FCBC1C` before releasing.

The vogella key is an ed25519 key.
Should the Portal reject the signature as an unsupported algorithm, sign that one release with an RSA 4096 key instead, it is the only case where a second key is needed.

## Cutting a release

The version in `pom.xml` is the version that gets published, there is no separate release plugin.

```bash
mvn clean verify -Prelease
mvn deploy -Prelease
```

The first command builds the jar, the sources jar, the javadoc jar and the signatures.
`gpg` asks for the passphrase of the signing key through the agent, so run it in a terminal that can prompt.

`autoPublish` is off, so `deploy` only uploads the deployment.
Check it under *Publish > Deployments* in the Portal and press *Publish* there.
The artifact appears on `repo1.maven.org` a few minutes later, and is searchable after some hours.

Finally tag the commit that was released:

```bash
git tag -s v1.0.0 -m "tycho-build-project-reactor 1.0.0"
git push origin v1.0.0
```

## After the release

Bump `<version>` in `pom.xml` for the next development cycle and update the version in `README.md` and in `examples/minimal-reactor/.mvn/maven.config`.
Every consuming repository pins the version literally, so a bump there is a one line change of `.mvn/maven.config`.
