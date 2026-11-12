# keycloak-grad-arma3-squad

A [Keycloak](https://www.keycloak.org/) Realm Resource Provider (SPI extension) that exposes members of a Keycloak group as an Arma 3-compatible `squad.xml` feed — the format Arma 3 uses for in-game squad/clan rosters.

Built by **Gruppe Adler**.

## What it does

The provider registers a new realm-scoped REST endpoint:

```
GET /realms/{realm}/arma3-squad/squad.xml
```

When called, it:

1. Reads per-realm configuration (enabled flag, group name, Steam ID attribute) from the realm's attributes.
2. Looks up the configured Keycloak group.
3. Collects all members of that group who have a Steam ID stored in their user attributes.
4. Renders an XML document following Arma 3's `squad.dtd` schema, including a `squad.xsl` stylesheet reference so the feed renders nicely in a browser as well as in-game.

It also serves the supporting static assets needed for a complete squad feed:

- `squad.dtd` — the DTD referenced by the generated XML
- `web/squad.xsl` + `web/squad.css` — XSLT/CSS used to render the feed as a styled HTML page in a browser
- `web/logo.svg`, `web/adlerkopp.svg`, `web/favicon.ico`, `web/steam.svg` — branding/icon assets
- `logo.paa` — Arma 3 native texture format logo, for in-game display
- `template.xml` — a template for the squad XML structure

Static files are served via:

```
GET /realms/{realm}/arma3-squad/{filename}
GET /realms/{realm}/arma3-squad/web/{filename}
```

with basic path-traversal protection (rejects filenames containing `..`, `/`, or `\`).

## Configuration

Configuration is stored as **realm attributes**, so it persists across restarts and can be managed via the Keycloak Admin Console (Realm Settings → Attributes) or the Admin REST API.

| Attribute                     | Description                                             | Default       |
|-------------------------------|---------------------------------------------------------|---------------|
| `arma3squad.enabled`          | Enables/disables the `squad.xml` endpoint for the realm | `false`       |
| `arma3squad.groupName`        | Name of the Keycloak group whose members are listed     | `arma3-squad` |
| `arma3squad.steamIdAttribute` | User attribute holding the Steam 64-bit ID              | `steamId`     |

Only group members with a non-blank value in the configured Steam ID attribute are included in the feed.

### Steam ID attribute and the user profile

Since Keycloak 24 the declarative user profile is always active, and it **silently drops user attributes it doesn't know about**. If the Steam ID attribute isn't declared, setting it on a user appears to succeed but nothing is stored, and `squad.xml` comes out without any members.

For each realm, do one of the following:

- **Declare the attribute (recommended):** *Realm settings → User profile → Create attribute*, named `steamId` (or whatever `arma3squad.steamIdAttribute` is set to). Use the attribute's permissions to control who can view and edit it.
- **Allow unmanaged attributes:** *Realm settings → General → Unmanaged attributes* → *Enabled* (or one of the admin-only options).

### Known limitation: empty squads

If no group member has a Steam ID, the feed contains no `<member>` elements. `squad.dtd` requires at least one member (`member+`), so that document does not validate against the DTD.

### Example: enable via Admin REST API

```http
PATCH /admin/realms/{realm}
Content-Type: application/json

{
  "attributes": {
    "arma3squad.enabled": "true",
    "arma3squad.groupName": "arma3-squad",
    "arma3squad.steamIdAttribute": "steamId"
  }
}
```

## Tech stack

- **Kotlin** 2.0.0 (JVM target 21)
- **Keycloak SPI** (`keycloak-core`, `keycloak-server-spi`, `keycloak-server-spi-private`, `keycloak-services`) — version 26.6.3, provided at runtime by the Keycloak server
- **Jakarta RS** 3.1.0 for the REST endpoints
- **Maven** build, packaged as a shaded (fat) JAR via `maven-shade-plugin`, with Keycloak/Jakarta RS dependencies excluded to avoid classpath conflicts with the host server

## Project layout

```
src/main/kotlin/de/grad/keycloak/squad/
├── provider/
│   ├── SquadResourceProvider.kt         # JAX-RS endpoints (squad.xml, static file serving)
│   └── SquadResourceProviderFactory.kt  # SPI factory + admin config metadata
└── config/
    └── SquadRealmConfig.kt              # Realm-attribute-backed configuration

src/main/resources/
├── META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory
└── static/
    ├── squad.dtd
    ├── template.xml
    ├── logo.paa
    └── web/
        ├── squad.xsl
        ├── squad.css
        ├── logo.svg
        ├── adlerkopp.svg
        ├── steam.svg
        └── favicon.ico
```

Keycloak discovers the provider via the standard `ServiceLoader` file at
`META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory`, which points to `SquadResourceProviderFactory`. The provider ID `arma3-squad` becomes the URL path segment for all endpoints.

## Building

```bash
mvn package
```

This produces a shaded JAR at `target/keycloak-grad-arma3-squad-<version>.jar`.

## Testing

```bash
mvn test     # unit tests
mvn verify   # unit tests + integration test against a real Keycloak (requires Docker)
```

- **Unit tests** (JUnit + MockK) cover realm config parsing, `squad.xml` generation (member filtering, XML escaping, validation against `squad.dtd`), static file serving (path traversal, content types, XSL base href) and SPI discovery.
- **Integration test** (`SquadProviderIT`, run by `maven-failsafe-plugin` in `verify`) starts `quay.io/keycloak/keycloak:<keycloak.version>` via Testcontainers, deploys the shaded JAR, creates a realm, group and users through the admin API, and calls the endpoints over HTTP. Because the image tag comes from the POM, Keycloak version bumps are tested against the matching server.

## Installation

1. Build (or download) the shaded JAR.
2. Copy it into your Keycloak server's `providers/` directory.
3. Run `kc.sh build` (or `kc.bat build`) to rebuild the Keycloak provider registry.
4. Restart Keycloak.
5. Enable the provider for a realm and configure it via the realm attributes described above.
6. Point your Arma 3 squad/clan configuration at:

   ```
   https://your-keycloak-host/realms/{realm}/arma3-squad/squad.xml
   ```

## CI / Releases

Every pull request and every push to `main` runs `mvn verify` (`.github/workflows/build.yml`), including the integration test.

A GitHub Actions workflow (`.github/workflows/publish.yml`) builds the project and publishes it to GitHub Packages whenever a GitHub Release is published. The release tag is used to set the Maven project version before packaging.

## Repository

<https://github.com/gruppe-adler/keycloak-grad-arma3-squad>
