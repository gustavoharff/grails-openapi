package dev.harff.grails.openapi;

/**
 * Command-line vocabulary shared by the Gradle task that writes the arguments and the
 * {@code generate-openapi} command that reads them.
 */
public final class OpenapiOptions {

    /** Opens a new document; every option after it belongs to that document. */
    public static final String DOCUMENT = "document";
    public static final String TITLE = "title";
    public static final String VERSION = "version";
    public static final String DESCRIPTION = "description";
    /** Repeatable. {@code url} or {@code url|description}. */
    public static final String SERVER = "server";
    /** Repeatable, and comma-separated values are accepted. */
    public static final String INCLUDE = "include";
    /** Repeatable, and comma-separated values are accepted. */
    public static final String EXCLUDE = "exclude";
    public static final String OUTPUT = "output";

    /** Separates a server URL from its description in a {@code --server} value. */
    public static final String SERVER_DESCRIPTION_SEPARATOR = "|";

    private OpenapiOptions() {
    }
}
