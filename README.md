# grails-openapi

> **Warning:** This library is currently under active development. APIs may change, and it may not be stable for production use.

A Gradle plugin for Grails applications that automatically generates [OpenAPI 3.0.3](https://spec.openapis.org/oas/v3.0.3) specification documents by introspecting your controllers, URL mappings, and domain classes.

## Installation

Add the plugin to your Grails application's `build.gradle`:

```gradle
buildscript {
    dependencies {
        classpath "io.github.gustavoharff:grails-openapi:0.1.0"
    }
}
```

```gradle
dependencies {
    implementation("io.github.gustavoharff:grails-openapi:0.1.0")
}
```

## Usage

Run the generation task:

```bash
./gradlew generateOpenapi
```

The spec is written to `build/openapi.yaml`.

## Configuration

Without an `openapi { }` block the plugin generates a single document at `build/openapi.yaml`
describing every endpoint it finds. Add the block to label that document, and to declare
further documents scoped to a slice of the API:

```gradle
openapi {
    title = 'Internal API'
    version = '1.16.0'
    output = 'build/openapi.yaml'

    document('public') {
        title = 'Public API'
        version = '1.0.0'
        description = 'Read your monitoring data from outside.'
        servers = ['https://api.example.com']
        includePaths = ['/public/v1/**']
        output = 'build/openapi-public.yaml'
    }
}
```

`./gradlew generateOpenapi` writes every declared document in one run. Each named document
also gets a task of its own — `generateOpenapiPublic` above — so CI can build just one.

### Properties

Properties set directly on the block configure the default document; the same properties are
available inside each `document(name)` block. A document that leaves a property unset falls
back to the plugin default rather than to the surrounding block, so every document stands on
its own.

| Property | Default | Description |
|---|---|---|
| `title` | `API` | `info.title` |
| `version` | `1.0.0` | `info.version` |
| `description` | — | `info.description`, omitted when unset |
| `servers` | `['/']` | Server URLs, or `[url: '...', description: '...']` maps |
| `includePaths` | every path | Ant-style globs a path must match to be included |
| `excludePaths` | — | Ant-style globs that keep a path out |
| `output` | `build/openapi.yaml`, or `build/openapi-<name>.yaml` for a named document | Where the document is written, relative to the project directory unless absolute |

### Scoping a document

`includePaths` and `excludePaths` are matched against the path as it appears in the document
(`/public/v1/comments/{id}`), using [Ant-style](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/util/AntPathMatcher.html)
globs. An exclusion always wins over an inclusion, and an empty `includePaths` means every path.

A scoped document only carries the components its own paths reach: `components.schemas` is cut
back to the transitive closure of the schemas the emitted paths reference — following `$ref`
through properties, array items, `allOf`/`oneOf`/`anyOf` and `additionalProperties` — and a
security scheme no emitted operation requires is dropped along with the document-wide
requirement.

### Generating by hand

The Gradle task drives the same command line you can type yourself:

```bash
grails generate-openapi --title='Public API' --include='/public/v1/**' --output=build/openapi-public.yaml
```

| Option | Description |
|---|---|
| `--document=<name>` | Opens another document; the options that follow belong to it |
| `--title=<text>` | `info.title` |
| `--version=<text>` | `info.version` |
| `--description=<text>` | `info.description` |
| `--server=<url>` | Repeatable. `url` or `url\|description` |
| `--include=<glob>` | Repeatable, and comma-separated globs are accepted |
| `--exclude=<glob>` | Repeatable, and comma-separated globs are accepted |
| `--output=<path>` | Where to write the document |

## Annotations

Fine-tune the generated spec using these annotations in your controllers and actions:

| Annotation | Target | Description |
|---|---|---|
| `@ApiIgnore` | controller, action | Exclude from the spec |
| `@ApiPublic` | controller, action | Mark as public (no auth required) |
| `@ApiTag("Name")` | controller, action | Group operations under a tag |
| `@Description("text")` | action | Operation summary/description |
| `@ApiResponse(status=404, description="...")` | action | Document a specific response |
| `@ApiResponses({...})` | action | Document multiple responses |
| `@Deprecated` | action | Mark operation as deprecated |

## How it works

The plugin resolves URL mappings to endpoints, introspects controller annotations, analyzes Groovy AST to detect `respond()` return types, builds JSON schemas from Grails command objects and domain classes (including GORM constraints), and serializes everything as a valid OpenAPI 3.0.3 YAML document.

**Conventions applied automatically:**

- `DELETE` actions → `204 No Content`
- `POST` save actions → `201 Created`
- Command objects on `POST`/`PUT`/`PATCH` → request body schema
- Command objects on `GET` → query parameters
- Path parameters extracted from URL mapping patterns
- Only paths a URL mapping actually resolves to are emitted: a controller reachable solely through an explicit mapping is not also published under its conventional `/controller-name` path
- Bearer JWT security applied globally; `@ApiPublic` removes the requirement
- Components no emitted path reaches are pruned from the document
- GORM constraints (`nullable`, `maxSize`, `min`, `max`, `inList`) mapped to OpenAPI schema constraints

## Requirements

- Grails 7.0.2+
- Java 17+

## License

[Apache License 2.0](LICENSE)
