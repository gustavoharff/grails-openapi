package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.ResolvedEndpoint
import grails.core.GrailsApplication
import org.grails.core.DefaultGrailsControllerClass

class UrlMappingResolver {

    GrailsApplication grailsApplication

    List<ResolvedEndpoint> resolveAll(def urlMappingsHolder) {
        List<ResolvedEndpoint> results = []
        Set<String> seenPaths = []

        // Pre-scan: collect every controller+action+method already covered by a specific (concrete) route.
        // Generic expansion will skip these to avoid duplicate / wrong-path entries.
        Set<String> specificCoverage = []
        urlMappingsHolder.urlMappings.each { mapping ->
            String cn = mapping.controllerName
            String method = mapping.httpMethod?.toUpperCase()
            String action = mapping.actionName
            if (cn && method && action && method != 'ANY') {
                specificCoverage << "${method}:${toLogical(cn)}:${action}"
            }
        }

        urlMappingsHolder.urlMappings.each { mapping ->
            String rawPattern = reconstructPattern(mapping)
            if (!rawPattern) return

            String httpMethod = mapping.httpMethod?.toUpperCase()
            if (!httpMethod || httpMethod == 'ANY') return

            String actionName = mapping.actionName
            if (!actionName) return

            if (isCatchAll(rawPattern)) return

            String controllerName = mapping.controllerName
            if (controllerName) {
                DefaultGrailsControllerClass ctrl = findController(controllerName)
                if (!ctrl) return
                ResolvedEndpoint ep = buildEndpoint(httpMethod, rawPattern, ctrl.logicalPropertyName, actionName, ctrl)
                if (ep && addIfNew(ep, seenPaths)) results << ep
            } else {
                grailsApplication.getArtefacts('Controller').each { artefact ->
                    DefaultGrailsControllerClass ctrl = artefact as DefaultGrailsControllerClass
                    if (!hasAction(ctrl, actionName)) return

                    // Skip if a specific route already covers this controller+action+method
                    String coverageKey = "${httpMethod}:${ctrl.logicalPropertyName}:${actionName}"
                    if (specificCoverage.contains(coverageKey)) return

                    ResolvedEndpoint ep = buildEndpoint(httpMethod, rawPattern, ctrl.logicalPropertyName, actionName, ctrl)
                    if (ep && addIfNew(ep, seenPaths)) results << ep
                }
            }
        }

        return results
    }

    /**
     * Grails stores URL patterns as regex-like notation: /(*)/(*)/(.*)?
     * Reconstruct the $varName-style path using the ordered constraints list.
     */
    private static String reconstructPattern(def mapping) {
        String urlPattern = mapping.urlData?.urlPattern
        if (!urlPattern) return null

        def constraints = mapping.constraints?.toList() ?: []
        if (constraints.isEmpty()) return urlPattern

        int idx = 0
        List<String> segments = []

        urlPattern.split('/').each { String seg ->
            if (!seg) return
            if (seg =~ /^\(.*\)\??$/) {
                boolean optional = seg.endsWith(')?')
                if (idx < constraints.size()) {
                    String varName = constraints[idx++].propertyName
                    segments << (optional ? "\$${varName}?" : "\$${varName}")
                }
            } else {
                segments << seg
            }
        }

        return '/' + segments.join('/')
    }

    private ResolvedEndpoint buildEndpoint(String httpMethod, String rawPattern, String logicalControllerName,
                                           String actionName, DefaultGrailsControllerClass ctrl) {
        String namespace = resolveNamespace(ctrl)
        String controllerPath = toKebabCase(logicalControllerName)

        List<String> pathParams = []
        String path = convertPattern(rawPattern, namespace, controllerPath, actionName, pathParams)

        return new ResolvedEndpoint(
            httpMethod: httpMethod,
            path: path,
            controllerName: logicalControllerName,
            actionName: actionName,
            pathParams: pathParams,
            controllerArtefact: ctrl
        )
    }

    private String convertPattern(String raw, String namespace, String controllerPath,
                                  String actionName, List<String> pathParams) {
        List<String> segments = raw.split('/').findAll { it }
        List<String> result = []

        segments.each { String seg ->
            if (seg == '$namespace') {
                if (namespace) result << namespace
            } else if (seg == '$controller') {
                result << controllerPath
            } else if (seg == '$action') {
                result << actionName
            } else if (seg =~ /^\$(\w+)\?$/) {
                String name = seg[1..-2]
                result << "{${name}}"
                if (!pathParams.contains(name)) pathParams << name
            } else if (seg =~ /^\$(\w+)$/) {
                String name = seg[1..-1]
                result << "{${name}}"
                if (!pathParams.contains(name)) pathParams << name
            } else {
                result << seg
            }
        }

        return '/' + result.join('/')
    }

    private static String resolveNamespace(DefaultGrailsControllerClass ctrl) {
        try {
            def field = ctrl.clazz.getDeclaredField('namespace')
            if (field) {
                field.accessible = true
                return field.get(null) as String ?: ''
            }
        } catch (Exception ignored) {}
        return ''
    }

    private static String toKebabCase(String logicalName) {
        logicalName
            .replaceAll(/([A-Z])/) { '-' + it[0].toLowerCase() }
            .replaceAll(/^-/, '')
    }

    /** Normalize a controller name from URL mapping DSL to Grails logical property name (camelCase, lowercase first). */
    private static String toLogical(String controllerName) {
        if (!controllerName) return controllerName
        return controllerName[0].toLowerCase() + controllerName.substring(1)
    }

    private DefaultGrailsControllerClass findController(String controllerName) {
        try {
            def ctrl = grailsApplication.getArtefactByLogicalPropertyName('Controller', controllerName)
            if (ctrl) return ctrl as DefaultGrailsControllerClass
            // DSL may use PascalCase; try lowercasing first letter
            String logical = toLogical(controllerName)
            ctrl = grailsApplication.getArtefactByLogicalPropertyName('Controller', logical)
            if (ctrl) return ctrl as DefaultGrailsControllerClass
        } catch (Exception ignored) {}
        return null
    }

    private static boolean hasAction(DefaultGrailsControllerClass ctrl, String actionName) {
        try {
            return ctrl.clazz.getMethods().any { it.name == actionName }
        } catch (Exception ignored) {
            return false
        }
    }

    private static boolean isCatchAll(String pattern) {
        return pattern.contains('$controller') && pattern.contains('$action')
    }

    private static boolean addIfNew(ResolvedEndpoint ep, Set<String> seen) {
        String key = "${ep.httpMethod}:${ep.path}"
        if (seen.contains(key)) return false
        seen << key
        return true
    }
}
