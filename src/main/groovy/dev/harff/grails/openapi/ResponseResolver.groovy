package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.EndpointInfo
import dev.harff.grails.openapi.model.ResolvedEndpoint
import grails.core.GrailsApplication

import java.lang.reflect.TypeVariable

class ResponseResolver {

    GrailsApplication grailsApplication
    Map<String, Map> schemas

    Map<String, Object> resolve(EndpointInfo info, ResolvedEndpoint ep) {
        if (info.responsesOverride) {
            return buildResponsesFromOverride(info.responsesOverride)
        }

        if (ep.httpMethod == 'DELETE' || ep.actionName == 'delete') {
            return ['204': [description: 'No Content']]
        }

        if (info.responseType) {
            return buildResponseForClass(info.responseType, info.responseIsList, ep, info.responseTypeArguments ?: [])
        }

        def domainClass = findDomainClass(ep.controllerName)
        if (domainClass) {
            String schemaName = domainClass.shortName
            if (!schemas.containsKey(schemaName)) {
                schemas[schemaName] = SchemaBuilder.buildDomainSchema(domainClass)
            }
            Map<String, Object> schemaRef = ['$ref': "#/components/schemas/${schemaName}".toString()]
            Map<String, Object> schema = ep.actionName == 'index'
                ? [type: 'array', items: schemaRef]
                : schemaRef

            int status = (ep.httpMethod == 'POST' && ep.actionName == 'save') ? 201 : 200
            return [(status.toString()): buildJsonResponse('Success', schema)]
        }

        return ['200': buildJsonResponse('Success', [type: 'object'])]
    }

    private Map<String, Object> buildResponseForClass(Class<?> cls, boolean isList, ResolvedEndpoint ep, List<Class<?>> typeArguments = []) {
        String schemaName = buildSchemaName(cls, typeArguments)
        if (!schemas.containsKey(schemaName)) {
            Map<String, Class<?>> typeBindings = buildTypeBindings(cls, typeArguments)
            def domainArtefact = grailsApplication.getArtefact('Domain', cls.simpleName)
            schemas[schemaName] = domainArtefact
                ? SchemaBuilder.buildDomainSchema(domainArtefact)
                : SchemaBuilder.buildObjectSchema(cls, typeBindings)
        }

        Map<String, Object> schemaRef = ['$ref': "#/components/schemas/${schemaName}".toString()]
        Map<String, Object> schema = isList ? [type: 'array', items: schemaRef] : schemaRef

        int status = (ep.httpMethod == 'POST' && ep.actionName == 'save') ? 201 : 200
        return [(status.toString()): buildJsonResponse('Success', schema)]
    }

    private static String buildSchemaName(Class<?> cls, List<Class<?>> typeArguments) {
        if (!typeArguments) return cls.simpleName
        String args = typeArguments.collect { it.simpleName }.join('And')
        return "${cls.simpleName}Of${args}"
    }

    private static Map<String, Class<?>> buildTypeBindings(Class<?> cls, List<Class<?>> typeArguments) {
        if (!typeArguments) return [:]
        TypeVariable[] typeParams = cls.typeParameters
        Map<String, Class<?>> bindings = [:]
        typeParams.eachWithIndex { TypeVariable tv, int i ->
            if (i < typeArguments.size()) {
                bindings[tv.name] = typeArguments[i]
            }
        }
        return bindings
    }

    private def findDomainClass(String controllerName) {
        List<String> candidates = [
            controllerName,
            controllerName.replaceFirst(/ies$/, 'y'),
            controllerName.replaceFirst(/s$/, ''),
        ]

        for (String candidate : candidates) {
            if (!candidate) continue
            try {
                def dc = grailsApplication.getArtefact('Domain', candidate.capitalize())
                if (dc) return dc
            } catch (Exception ignored) {}
        }
        return null
    }

    private static Map<String, Object> buildJsonResponse(String description, Map<String, Object> schema) {
        return [
            description: description,
            content: [
                'application/json': [schema: schema]
            ]
        ]
    }

    private static Map<String, Object> buildResponsesFromOverride(List<Map> overrides) {
        Map<String, Object> responses = [:]
        overrides.each { Map r ->
            String key = r.status.toString()
            responses[key] = [description: r.description ?: 'Success']
        }
        return responses
    }
}
