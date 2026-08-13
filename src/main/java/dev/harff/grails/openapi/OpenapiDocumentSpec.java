package dev.harff.grails.openapi;

import java.util.ArrayList;
import java.util.List;

/**
 * Declarative description of a single OpenAPI document.
 *
 * <p>The root {@code openapi { }} block configures the default document; every
 * {@code document('name') { }} block inside it adds another one.
 */
public class OpenapiDocumentSpec {

    /** Name of the document configured by the root {@code openapi { }} block. */
    public static final String DEFAULT_NAME = "default";

    private final String name;

    private String title;
    private String version;
    private String description;
    private String output;
    private List<Object> servers = new ArrayList<>();
    private List<String> includePaths = new ArrayList<>();
    private List<String> excludePaths = new ArrayList<>();

    public OpenapiDocumentSpec(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("An OpenAPI document name must not be empty");
        }
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isDefaultDocument() {
        return DEFAULT_NAME.equals(name);
    }

    /** True when at least one value was set, i.e. the document overrides plugin defaults. */
    public boolean isConfigured() {
        return title != null
                || version != null
                || description != null
                || output != null
                || !servers.isEmpty()
                || !includePaths.isEmpty()
                || !excludePaths.isEmpty();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** Output path of the generated document, relative to the project directory unless absolute. */
    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    /** Server URLs, either as {@code String} or as {@code [url: '...', description: '...']} maps. */
    public List<Object> getServers() {
        return servers;
    }

    public void setServers(List<Object> servers) {
        this.servers = servers == null ? new ArrayList<>() : new ArrayList<>(servers);
    }

    /** Ant-style globs matched against the generated path; empty means "every path". */
    public List<String> getIncludePaths() {
        return includePaths;
    }

    public void setIncludePaths(List<String> includePaths) {
        this.includePaths = includePaths == null ? new ArrayList<>() : new ArrayList<>(includePaths);
    }

    /** Ant-style globs matched against the generated path; these win over {@link #getIncludePaths()}. */
    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths == null ? new ArrayList<>() : new ArrayList<>(excludePaths);
    }
}
