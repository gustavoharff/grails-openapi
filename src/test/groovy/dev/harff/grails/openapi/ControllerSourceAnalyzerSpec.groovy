package dev.harff.grails.openapi

import spock.lang.Specification

class ControllerSourceAnalyzerSpec extends Specification {

    // --------------- parseImports ---------------

    def "parseImports extracts simple class name from import"() {
        given:
        def source = '''\
            package com.example
            import com.example.dto.PaginationResult
            class MyController {}
        '''.stripIndent()

        when:
        def imports = ControllerSourceAnalyzer.parseImports(source)

        then:
        imports['PaginationResult'] == 'com.example.dto.PaginationResult'
    }

    def "parseImports extracts alias when 'as' is used"() {
        given:
        def source = '''\
            import com.example.dto.PaginationResult as PagedResult
            class MyController {}
        '''.stripIndent()

        when:
        def imports = ControllerSourceAnalyzer.parseImports(source)

        then:
        imports['PagedResult'] == 'com.example.dto.PaginationResult'
        !imports.containsKey('PaginationResult')
    }

    def "parseImports handles multiple imports"() {
        given:
        def source = '''\
            import com.example.dto.PaginationResult
            import com.example.domain.FacebookAccount
            class MyController {}
        '''.stripIndent()

        when:
        def imports = ControllerSourceAnalyzer.parseImports(source)

        then:
        imports['PaginationResult'] == 'com.example.dto.PaginationResult'
        imports['FacebookAccount'] == 'com.example.domain.FacebookAccount'
    }

    def "parseImports ignores non-import lines"() {
        given:
        def source = '''\
            package com.example
            // import com.example.Ignored
            class MyController {
                def index() { respond result }
            }
        '''.stripIndent()

        when:
        def imports = ControllerSourceAnalyzer.parseImports(source)

        then:
        !imports.containsKey('Ignored')
    }

    def "parseImports returns empty map when no imports present"() {
        given:
        def source = '''\
            package com.example
            class MyController {}
        '''.stripIndent()

        when:
        def imports = ControllerSourceAnalyzer.parseImports(source)

        then:
        imports.isEmpty()
    }

    def "parseImports handles star imports without adding them to the map"() {
        given:
        def source = '''\
            import com.example.dto.*
            import com.example.domain.FacebookAccount
            class MyController {}
        '''.stripIndent()

        when:
        def imports = ControllerSourceAnalyzer.parseImports(source)

        then:
        // star imports don't produce a usable entry
        !imports.containsKey('*')
        imports['FacebookAccount'] == 'com.example.domain.FacebookAccount'
    }

    // --------------- resolveClass via currentImports ---------------

    def "resolveClass resolves class found via parsed imports"() {
        given:
        def analyzer = new ControllerSourceAnalyzer()
        // Simulate what doAnalyze does: populate currentImports with a known class
        analyzer.currentImports = ['PaginationResult': String.name]

        when:
        Class<?> resolved = analyzer.resolveClass('PaginationResult')

        then:
        // String is used as a stand-in for any importable class
        resolved == String
    }

    def "resolveClass returns null when name not in imports and not otherwise resolvable"() {
        given:
        def analyzer = new ControllerSourceAnalyzer()
        analyzer.currentImports = [:]

        when:
        Class<?> resolved = analyzer.resolveClass('UnresolvableClassName')

        then:
        resolved == null
    }
}
