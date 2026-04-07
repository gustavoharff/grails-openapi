package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.EndpointInfo

class SecurityResolver {

    List<Map> resolveEndpointSecurity(EndpointInfo info) {
        if (info.isPublic) {
            return []
        }
        return null  // null = omit key, inherits global security
    }

    static Map<String, Object> buildSecuritySchemes() {
        return [
            bearerAuth: [
                type: 'http',
                scheme: 'bearer',
                bearerFormat: 'JWT'
            ]
        ]
    }
}
