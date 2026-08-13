package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.DocumentConfig

/**
 * Turns the {@code generate-openapi} command line into the documents to generate.
 *
 * <pre>
 * generate-openapi --include=/public/v1/** --output=build/openapi-public.yaml
 * </pre>
 *
 * Options apply to the document currently being described. {@code --document=name} opens
 * a new one, so a single run can write several files. Options that appear before any
 * {@code --document} describe the default document, and a command line with no options at
 * all describes exactly that default document.
 */
class OpenapiArgsParser {

    static List<DocumentConfig> parse(List<String> args) {
        List<DocumentConfig> documents = []
        DocumentConfig current = null

        (args ?: []).each { String arg ->
            if (!arg?.startsWith('--')) return

            int separator = arg.indexOf('=')
            if (separator < 0) return

            String option = arg.substring(2, separator).trim()
            String value = arg.substring(separator + 1)

            if (option == OpenapiOptions.DOCUMENT) {
                current = new DocumentConfig(name: value)
                documents << current
                return
            }

            if (!isKnown(option)) return

            if (current == null) {
                current = new DocumentConfig()
                documents << current
            }

            if (option == OpenapiOptions.TITLE) current.title = value
            else if (option == OpenapiOptions.VERSION) current.version = value
            else if (option == OpenapiOptions.DESCRIPTION) current.description = value
            else if (option == OpenapiOptions.OUTPUT) current.output = value
            else if (option == OpenapiOptions.SERVER) current.servers << toServer(value)
            else if (option == OpenapiOptions.INCLUDE) current.includePaths.addAll(split(value))
            else if (option == OpenapiOptions.EXCLUDE) current.excludePaths.addAll(split(value))
        }

        return documents ?: [new DocumentConfig()]
    }

    private static boolean isKnown(String option) {
        return option in [
            OpenapiOptions.TITLE,
            OpenapiOptions.VERSION,
            OpenapiOptions.DESCRIPTION,
            OpenapiOptions.OUTPUT,
            OpenapiOptions.SERVER,
            OpenapiOptions.INCLUDE,
            OpenapiOptions.EXCLUDE,
        ]
    }

    /** {@code https://api.example.com} or {@code https://api.example.com|Production}. */
    private static Map<String, String> toServer(String value) {
        int separator = value.indexOf(OpenapiOptions.SERVER_DESCRIPTION_SEPARATOR)
        if (separator < 0) return [url: value]
        return [
            url        : value.substring(0, separator),
            description: value.substring(separator + 1),
        ]
    }

    private static List<String> split(String value) {
        return value.split(',').collect { it.trim() }.findAll { it }
    }
}
