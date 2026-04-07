package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.EndpointInfo
import dev.harff.grails.openapi.model.ResolvedEndpoint
import grails.core.GrailsApplication

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
            return buildResponseForClass(info.responseType, info.responseIsList, ep)
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

    private Map<String, Object> buildResponseForClass(Class<?> cls, boolean isList, ResolvedEndpoint ep) {
        String schemaName = cls.simpleName
        if (!schemas.containsKey(schemaName)) {
            def domainArtefact = grailsApplication.getArtefact('Domain',cls.simpleName)
            schemas[schemaName] = domainArtefact
                ? SchemaBuilder.buildDomainSchema(domainArtefact)
                : SchemaBuilder.buildObjectSchema(cls)
        }

        Map<String, Object> schemaRef = ['$ref': "#/components/schemas/${schemaName}".toString()]
        Map<String, Object> schema = isList ? [type: 'array', items: schemaRef] : schemaRef

        int status = (ep.httpMethod == 'POST' && ep.actionName == 'save') ? 201 : 200
        return [(status.toString()): buildJsonResponse('Success', schema)]
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
                def dc = grailsApplication.getArtefact('Domain',candidate.capitalize())
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
