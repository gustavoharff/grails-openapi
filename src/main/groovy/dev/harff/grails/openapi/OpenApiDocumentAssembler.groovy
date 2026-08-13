package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.DocumentConfig
import dev.harff.grails.openapi.model.EndpointInfo
import dev.harff.grails.openapi.model.ResolvedEndpoint
import grails.core.GrailsApplication
import grails.validation.Validateable

import java.lang.reflect.Field
import java.lang.reflect.Modifier

class OpenApiDocumentAssembler {

    GrailsApplication grailsApplication

    Map<String, Object> assemble(def urlMappingsHolder, DocumentConfig config = new DocumentConfig()) {
        Map<String, Map> schemas = [:]
        Map<String, Map> paths = [:]

        UrlMappingResolver resolver = new UrlMappingResolver(grailsApplication: grailsApplication)
        ControllerSourceAnalyzer sourceAnalyzer = new ControllerSourceAnalyzer(grailsApplication: grailsApplication)
        ControllerIntrospector introspector = new ControllerIntrospector(sourceAnalyzer: sourceAnalyzer)
        SecurityResolver security = new SecurityResolver()
        ResponseResolver responseResolver = new ResponseResolver(grailsApplication: grailsApplication, schemas: schemas)

        // Scope the document before anything is introspected, so only the schemas its own
        // paths need are ever built.
        PathFilter pathFilter = new PathFilter(config)
        List<ResolvedEndpoint> endpoints = resolver.resolveAll(urlMappingsHolder)
            .findAll { pathFilter.accepts(it.path) }

        // Introspect all endpoints once, then compute unique schema names up-front
        List<Tuple2<ResolvedEndpoint, EndpointInfo>> resolved = endpoints.collect { ep ->
            new Tuple2(ep, introspector.introspect(ep.controllerArtefact, ep.actionName, ep.httpMethod))
        }
        List<Class<?>> bodyClasses = resolved
            .findAll { it.v2?.commandIsBody }
            .collect { it.v2.commandClass }
            .unique()
        Map<Class<?>, String> schemaNames = computeSchemaNames(bodyClasses)

        resolved.each { Tuple2<ResolvedEndpoint, EndpointInfo> pair ->
            ResolvedEndpoint ep = pair.v1
            EndpointInfo info = pair.v2
            if (!info || info.apiIgnore) return

            List<Map> pathParameters = ep.pathParams.collect { String name ->
                [name: name, in: 'path', required: true, schema: [type: 'string']]
            }

            List<Map> queryParameters = []
            Map requestBody = null

            if (info.commandClass) {
                if (info.commandIsBody) {
                    String schemaName = schemaNames[info.commandClass]
                    if (!schemas.containsKey(schemaName)) {
                        schemas[schemaName] = SchemaBuilder.buildCommandSchema(info.commandClass)
                    }
                    requestBody = [
                        required: true,
                        content: [
                            'application/json': [
                                schema: ['$ref': "#/components/schemas/${schemaName}".toString()]
                            ]
                        ]
                    ]
                } else {
                    queryParameters = buildQueryParams(info.commandClass, ep.pathParams)
                }
            }

            List<Map> allParameters = pathParameters + queryParameters

            Map responses = responseResolver.resolve(info, ep)

            String tag = info.apiTag ?: deriveTag(ep.controllerName)
            String operationId = deriveOperationId(ep.httpMethod, ep.path)

            Map operation = [
                tags       : [tag],
                operationId: operationId,
            ]
            if (info.description) operation.summary = info.description
            if (allParameters) operation.parameters = allParameters
            if (requestBody) operation.requestBody = requestBody
            operation.responses = responses
            if (info.deprecated) operation.deprecated = true

            List endpointSecurity = security.resolveEndpointSecurity(info)
            if (endpointSecurity != null) operation.security = endpointSecurity

            String method = ep.httpMethod.toLowerCase()
            if (!paths[ep.path]) paths[ep.path] = [:]
            paths[ep.path][method] = operation
        }

        return buildDocument(config, paths, schemas)
    }

    private static Map<String, Object> buildDocument(DocumentConfig config,
                                                     Map<String, Map> paths,
                                                     Map<String, Map> schemas) {
        Map<String, Object> info = [title: config.resolveTitle()]
        if (config.description) info.description = config.description
        info.version = config.resolveVersion()

        List<Map> globalSecurity = [[bearerAuth: []]]
        Map<String, Object> securitySchemes = SecurityResolver.buildSecuritySchemes()

        // With no operation to inherit it, the document-wide requirement cannot be judged
        // unused, so an empty document keeps the default security skeleton.
        if (ComponentPruner.operations(paths)) {
            securitySchemes = ComponentPruner.pruneSecuritySchemes(securitySchemes, globalSecurity, paths)
            if (!securitySchemes) globalSecurity = null
        }

        Map<String, Object> components = [:]
        if (securitySchemes) components.securitySchemes = securitySchemes
        components.schemas = ComponentPruner.pruneSchemas(schemas, paths).sort { a, b -> a.key <=> b.key }

        Map<String, Object> document = [:]
        document.openapi = '3.0.3'
        document.info = info
        document.servers = config.resolveServers()
        if (globalSecurity) document.security = globalSecurity
        document.paths = paths.sort { a, b -> a.key <=> b.key }
        document.components = components
        return document
    }

    private static List<Map> buildQueryParams(Class<?> commandClass, List<String> existingPathParams) {
        List<Map> params = []
        Map constrainedProperties = [:]
        try {
            constrainedProperties = commandClass.constrainedProperties ?: [:]
        } catch (Exception ignored) {}

        collectCommandFields(commandClass).each { Field field ->
            if (existingPathParams.contains(field.name)) return

            Map<String, Object> schema = TypeMapper.toSchema(field.type, field.genericType)
            def cp = constrainedProperties[field.name]
            boolean required = false
            try { required = cp?.nullable == false } catch (Exception ignored) {}
            try { if (cp?.inList) schema['enum'] = cp.inList } catch (Exception ignored) {}

            params << [name: field.name, in: 'query', required: required, schema: schema]
        }
        return params
    }

    private static List<Field> collectCommandFields(Class<?> cls) {
        List<Field> fields = []
        Class<?> current = cls
        while (current != null && current != Object && Validateable.isAssignableFrom(current)) {
            current.declaredFields.each { Field f ->
                if (!f.synthetic
                    && !Modifier.isStatic(f.modifiers)
                    && hasPublicGetter(current, f.name)
                    && f.name != 'metaClass'
                    && !f.name.startsWith('$')
                    && !f.name.contains('__')
                    && f.type != Closure
                    && !fields.any { it.name == f.name }) {
                    fields << f
                }
            }
            current = current.superclass
        }
        return fields
    }

    private static boolean hasPublicGetter(Class<?> cls, String fieldName) {
        String capitalized = fieldName.capitalize()
        return cls.methods.any { m ->
            Modifier.isPublic(m.modifiers)
            && m.parameterCount == 0
            && (m.name == "get${capitalized}" || m.name == "is${capitalized}")
        }
    }

    private static Map<Class<?>, String> computeSchemaNames(List<Class<?>> classes) {
        Map<Class<?>, String> names = [:]
        classes.groupBy { it.simpleName }.each { simpleName, group ->
            if (group.size() == 1) {
                names[group[0]] = simpleName
                return
            }
            int depth = 1
            while (depth <= 20) {
                Map<String, List<Class<?>>> byCandidate = group.groupBy { qualifiedSchemaName(it, depth) }
                if (byCandidate.values().every { it.size() == 1 }) {
                    group.each { names[it] = qualifiedSchemaName(it, depth) }
                    break
                }
                depth++
            }
        }
        return names
    }

    private static String qualifiedSchemaName(Class<?> cls, int depth) {
        // Split by both '.' and '$' to include inner-class segments as discriminators
        String[] parts = cls.name.split('[.$]')
        // Drop the last segment (simpleName) and use the remaining as the qualifier pool
        String[] qualifierParts = parts.length > 1 ? parts[0..<(parts.length - 1)] as String[] : new String[0]
        int start = Math.max(0, qualifierParts.length - depth)
        String prefix = qualifierParts[start..<qualifierParts.length].collect { it.capitalize() }.join('')
        return prefix + cls.simpleName
    }

    private static String deriveOperationId(String httpMethod, String path) {
        String prefix = httpMethod.toLowerCase()
        String segments = path.split('/').findAll { it }.collect { String seg ->
            if (seg =~ /^\{(\w+)\}$/) {
                String param = (seg =~ /^\{(\w+)\}$/)[0][1]
                'By' + param.capitalize()
            } else {
                seg.split('[^a-zA-Z0-9]+').collect { it.capitalize() }.join('')
            }
        }.join('')
        return prefix + segments
    }

    private static String deriveTag(String controllerName) {
        controllerName
            .replaceAll(/([A-Z])/) { ' ' + it[0] }
            .trim()
            .split(' ')
            .collect { it.capitalize() }
            .join(' ')
            ?: controllerName.capitalize()
    }
}
