package dev.harff.grails.openapi

/**
 * Drops components no emitted operation can reach.
 *
 * A scoped document introspects the whole application but publishes a slice of it, so the
 * component registries have to be cut back to what its paths actually reference.
 */
class ComponentPruner {

    private static final String SCHEMA_REF_PREFIX = '#/components/schemas/'

    /**
     * Keeps the schemas the paths reference plus everything those schemas reach in turn,
     * following $ref through properties, array items, allOf/oneOf/anyOf and
     * additionalProperties, however deeply they nest.
     */
    static Map<String, Map> pruneSchemas(Map<String, Map> schemas, Map<String, Map> paths) {
        Set<String> reachable = []
        List<String> pending = collectSchemaRefs(paths).toList()

        while (pending) {
            String name = pending.pop()
            if (!reachable.add(name)) continue
            Map schema = schemas[name]
            if (schema) pending.addAll(collectSchemaRefs(schema))
        }

        return schemas.findAll { reachable.contains(it.key) } as Map<String, Map>
    }

    /**
     * Keeps the security schemes at least one emitted operation requires. An operation
     * without its own {@code security} inherits the document-wide requirement.
     */
    static Map<String, Object> pruneSecuritySchemes(Map<String, Object> securitySchemes,
                                                    List<Map> globalSecurity,
                                                    Map<String, Map> paths) {
        Set<String> required = requiredSchemeNames(globalSecurity, paths)
        return securitySchemes.findAll { required.contains(it.key) } as Map<String, Object>
    }

    /** Names of the security schemes the emitted operations require, directly or by inheritance. */
    static Set<String> requiredSchemeNames(List<Map> globalSecurity, Map<String, Map> paths) {
        Set<String> required = []
        operations(paths).each { Map operation ->
            List requirements = operation.containsKey('security') ? operation.security : globalSecurity
            requirements?.each { Map requirement -> required.addAll(requirement.keySet()) }
        }
        return required
    }

    static List<Map> operations(Map<String, Map> paths) {
        return paths.values().collectMany { Map methods -> methods.values().toList() } as List<Map>
    }

    /** Every schema name referenced by a $ref anywhere below the given node. */
    private static Set<String> collectSchemaRefs(Object node) {
        Set<String> names = []
        collectSchemaRefs(node, names)
        return names
    }

    private static void collectSchemaRefs(Object node, Set<String> names) {
        if (node instanceof Map) {
            node.each { key, value ->
                if (key == '$ref' && value instanceof CharSequence) {
                    String ref = value.toString()
                    if (ref.startsWith(SCHEMA_REF_PREFIX)) {
                        names << ref.substring(SCHEMA_REF_PREFIX.length())
                    }
                } else {
                    collectSchemaRefs(value, names)
                }
            }
        } else if (node instanceof Collection) {
            node.each { collectSchemaRefs(it, names) }
        }
    }
}
