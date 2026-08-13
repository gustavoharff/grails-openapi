package dev.harff.grails.openapi;

import groovy.lang.Closure;
import org.gradle.api.Action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code openapi { }} extension.
 *
 * <pre>
 * openapi {
 *     title = 'Internal API'
 *     version = '1.16.0'
 *     output = 'build/openapi.yaml'
 *
 *     document('public') {
 *         title = 'Public API'
 *         includePaths = ['/public/v1/**']
 *         output = 'build/openapi-public.yaml'
 *     }
 * }
 * </pre>
 *
 * <p>Properties set directly on the block configure the default document; each
 * {@code document(name)} block declares an additional one.
 */
public class OpenapiExtension extends OpenapiDocumentSpec {

    private final Map<String, OpenapiDocumentSpec> namedDocuments = new LinkedHashMap<>();

    public OpenapiExtension() {
        super(DEFAULT_NAME);
    }

    public OpenapiDocumentSpec document(String name) {
        if (DEFAULT_NAME.equals(name)) {
            throw new IllegalArgumentException(
                    "'" + DEFAULT_NAME + "' is reserved for the document configured by the openapi { } block itself");
        }
        return namedDocuments.computeIfAbsent(name, OpenapiDocumentSpec::new);
    }

    public OpenapiDocumentSpec document(String name, Action<? super OpenapiDocumentSpec> action) {
        OpenapiDocumentSpec spec = document(name);
        action.execute(spec);
        return spec;
    }

    public OpenapiDocumentSpec document(String name, Closure<?> closure) {
        OpenapiDocumentSpec spec = document(name);
        closure.setDelegate(spec);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call(spec);
        return spec;
    }

    /** Every declared document: the default one first, then the named ones in declaration order. */
    public List<OpenapiDocumentSpec> resolveDocuments() {
        List<OpenapiDocumentSpec> documents = new ArrayList<>();
        documents.add(this);
        documents.addAll(namedDocuments.values());
        return documents;
    }
}
