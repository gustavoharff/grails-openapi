package dev.harff.grails.openapi;

import java.util.List;
import java.util.Map;

/**
 * Builds the {@code generate-openapi} command line the Gradle task hands to {@code runCommand}.
 *
 * <p>The command understands the very same arguments when typed by hand:
 *
 * <pre>
 * grails generate-openapi --include=/public/v1/** --output=build/openapi-public.yaml
 * </pre>
 *
 * <p>{@code --document=name} opens a new document; every option after it belongs to that
 * document, so a single run can write several files.
 */
public final class OpenapiCommandLine {

    public static final String COMMAND_NAME = "generate-openapi";

    private OpenapiCommandLine() {
    }

    public static String build(List<OpenapiDocumentSpec> documents) {
        StringBuilder line = new StringBuilder(COMMAND_NAME);

        // A lone, unconfigured default document needs no arguments at all, which keeps
        // `grails generate-openapi` the command line for a project without an openapi { } block.
        boolean omitDocumentOption = documents.size() == 1 && documents.get(0).isDefaultDocument();

        for (OpenapiDocumentSpec document : documents) {
            if (!omitDocumentOption) {
                option(line, OpenapiOptions.DOCUMENT, document.getName());
            }
            optionIfPresent(line, OpenapiOptions.TITLE, document.getTitle());
            optionIfPresent(line, OpenapiOptions.VERSION, document.getVersion());
            optionIfPresent(line, OpenapiOptions.DESCRIPTION, document.getDescription());
            for (Object server : document.getServers()) {
                option(line, OpenapiOptions.SERVER, serverValue(server));
            }
            for (String include : document.getIncludePaths()) {
                option(line, OpenapiOptions.INCLUDE, include);
            }
            for (String exclude : document.getExcludePaths()) {
                option(line, OpenapiOptions.EXCLUDE, exclude);
            }
            optionIfPresent(line, OpenapiOptions.OUTPUT, document.getOutput());
        }

        return line.toString();
    }

    /** {@code 'https://api.example.com'} or {@code [url: '...', description: '...']}. */
    private static String serverValue(Object server) {
        if (server instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) server;
            Object url = map.get("url");
            if (url == null) {
                throw new IllegalArgumentException("An OpenAPI server map must declare a 'url' entry: " + map);
            }
            Object description = map.get("description");
            return description == null
                    ? String.valueOf(url)
                    : String.valueOf(url) + OpenapiOptions.SERVER_DESCRIPTION_SEPARATOR + description;
        }
        return String.valueOf(server);
    }

    private static void optionIfPresent(StringBuilder line, String name, String value) {
        if (value != null) {
            option(line, name, value);
        }
    }

    private static void option(StringBuilder line, String name, String value) {
        line.append(' ').append("--").append(name).append('=').append(quote(value));
    }

    /**
     * Quotes a value for the Ant-style tokenizer Grails uses to split the command line.
     * That tokenizer has no escape character, so a value cannot carry both quote styles.
     */
    private static String quote(String value) {
        if (value.isEmpty()) {
            return "\"\"";
        }
        boolean needsQuoting = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'') {
                needsQuoting = true;
                break;
            }
        }
        if (!needsQuoting) {
            return value;
        }
        if (value.indexOf('"') < 0) {
            return '"' + value + '"';
        }
        if (value.indexOf('\'') < 0) {
            return '\'' + value + '\'';
        }
        throw new IllegalArgumentException(
                "An openapi { } value cannot contain both single and double quotes: " + value);
    }
}
