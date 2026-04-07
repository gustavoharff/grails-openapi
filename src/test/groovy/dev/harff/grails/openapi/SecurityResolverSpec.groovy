package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.EndpointInfo
import spock.lang.Specification

class SecurityResolverSpec extends Specification {

    SecurityResolver resolver = new SecurityResolver()

    def "public endpoint returns empty security list"() {
        given:
        def info = new EndpointInfo(isPublic: true)

        when:
        def security = resolver.resolveEndpointSecurity(info)

        then:
        security == []
    }

    def "non-public endpoint returns null (inherits global security)"() {
        given:
        def info = new EndpointInfo(isPublic: false)

        when:
        def security = resolver.resolveEndpointSecurity(info)

        then:
        security == null
    }

    def "buildSecuritySchemes returns bearerAuth JWT scheme"() {
        when:
        def schemes = SecurityResolver.buildSecuritySchemes()

        then:
        schemes.containsKey('bearerAuth')
        schemes.bearerAuth.type == 'http'
        schemes.bearerAuth.scheme == 'bearer'
        schemes.bearerAuth.bearerFormat == 'JWT'
    }

    def "buildSecuritySchemes returns exactly one scheme"() {
        when:
        def schemes = SecurityResolver.buildSecuritySchemes()

        then:
        schemes.size() == 1
    }
}
