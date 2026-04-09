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
- Bearer JWT security applied globally; `@ApiPublic` removes the requirement
- GORM constraints (`nullable`, `maxSize`, `min`, `max`, `inList`) mapped to OpenAPI schema constraints

## Requirements

- Grails 7.0.2+
- Java 17+

## License

[Apache License 2.0](LICENSE)
