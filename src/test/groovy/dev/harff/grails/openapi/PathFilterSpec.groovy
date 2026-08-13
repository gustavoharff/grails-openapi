package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.DocumentConfig
import spock.lang.Specification
import spock.lang.Unroll

class PathFilterSpec extends Specification {

    def "accepts every path when no globs are configured"() {
        given:
        def filter = new PathFilter(new DocumentConfig())

        expect:
        filter.accepts('/anything')
        filter.accepts('/public/v1/comments')
    }

    @Unroll
    def "includePaths #includes #verb #path"() {
        given:
        def filter = new PathFilter(includes, [])

        expect:
        filter.accepts(path) == accepted

        where:
        includes                          | path                          || accepted
        ['/public/v1/**']                 | '/public/v1/comments'         || true
        ['/public/v1/**']                 | '/public/v1/comments/{id}'    || true
        ['/public/v1/**']                 | '/public/v1'                  || true
        ['/public/v1/**']                 | '/public/v2/comments'         || false
        ['/public/v1/**']                 | '/internal/comments'          || false
        ['/public/v1/*']                  | '/public/v1/comments'         || true
        ['/public/v1/*']                  | '/public/v1/comments/{id}'    || false
        ['/admin/**', '/public/v1/**']    | '/admin/users'                || true
        ['/admin/**', '/public/v1/**']    | '/public/v1/comments'         || true
        ['/admin/**', '/public/v1/**']    | '/orders'                     || false
        ['/items/{id}']                   | '/items/{id}'                 || true

        verb = accepted ? 'accepts' : 'rejects'
    }

    @Unroll
    def "excludePaths #excludes #verb #path"() {
        given:
        def filter = new PathFilter([], excludes)

        expect:
        filter.accepts(path) == accepted

        where:
        excludes            | path                  || accepted
        ['/internal/**']    | '/internal/metrics'   || false
        ['/internal/**']    | '/public/v1/comments' || true
        ['/**/{id}']        | '/orders/{id}'        || false

        verb = accepted ? 'accepts' : 'rejects'
    }

    def "excludePaths wins over includePaths"() {
        given:
        def filter = new PathFilter(['/public/v1/**'], ['/public/v1/internal/**'])

        expect:
        filter.accepts('/public/v1/comments')
        !filter.accepts('/public/v1/internal/debug')
    }

    def "reads the globs from a document configuration"() {
        given:
        def config = new DocumentConfig(includePaths: ['/public/v1/**'], excludePaths: ['/public/v1/secret'])
        def filter = new PathFilter(config)

        expect:
        filter.accepts('/public/v1/comments')
        !filter.accepts('/public/v1/secret')
        !filter.accepts('/orders')
    }
}
