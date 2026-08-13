package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.DocumentConfig
import org.springframework.util.AntPathMatcher

/**
 * Decides whether a resolved path belongs in a document, using Ant-style globs
 * ({@code /public/v1/**}) matched against the path as it is written to the document.
 */
class PathFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher()

    private final List<String> includePaths
    private final List<String> excludePaths

    PathFilter(DocumentConfig config) {
        this(config?.includePaths, config?.excludePaths)
    }

    PathFilter(List<String> includePaths, List<String> excludePaths) {
        this.includePaths = includePaths ?: []
        this.excludePaths = excludePaths ?: []
    }

    boolean accepts(String path) {
        if (path == null) return false
        // An exclusion always wins, even over an explicit inclusion.
        if (excludePaths.any { MATCHER.match(it, path) }) return false
        if (!includePaths) return true
        return includePaths.any { MATCHER.match(it, path) }
    }
}
