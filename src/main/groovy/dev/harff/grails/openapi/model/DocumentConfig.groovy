package dev.harff.grails.openapi.model

import dev.harff.grails.openapi.OpenapiDocumentSpec

/**
 * Everything that scopes and labels one generated OpenAPI document.
 *
 * An instance with no values set describes the single document the plugin has always
 * produced: every path, titled 'API', version '1.0.0', served from '/'.
 */
class DocumentConfig {

    static final String DEFAULT_NAME = OpenapiDocumentSpec.DEFAULT_NAME

    String name = DEFAULT_NAME
    String title
    String version
    String description
    List<Map<String, String>> servers = []
    List<String> includePaths = []
    List<String> excludePaths = []
    String output

    String resolveTitle() {
        title ?: 'API'
    }

    String resolveVersion() {
        version ?: '1.0.0'
    }

    List<Map<String, String>> resolveServers() {
        servers ?: [[url: '/']]
    }

    String resolveOutput() {
        if (output) return output
        return name == DEFAULT_NAME ? 'build/openapi.yaml' : "build/openapi-${name}.yaml".toString()
    }
}
