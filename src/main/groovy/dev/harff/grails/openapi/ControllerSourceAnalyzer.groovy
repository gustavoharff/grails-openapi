package dev.harff.grails.openapi

import dev.harff.grails.openapi.model.RespondTypeInfo
import grails.core.GrailsApplication
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.ast.expr.ArgumentListExpression
import org.codehaus.groovy.ast.expr.ConstructorCallExpression
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.IfStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.ast.stmt.TryCatchStatement
import org.codehaus.groovy.control.CompilePhase

class ControllerSourceAnalyzer {

    GrailsApplication grailsApplication

    private static final Set<String> GORM_LIST_METHODS = [
        'list', 'findAll', 'findAllBy', 'executeQuery', 'getAll', 'listOrderBy', 'findAllWhere'
    ] as Set

    private static final Set<String> UNTYPED = ['Object', 'def', 'java.lang.Object'] as Set

    private final Map<String, Map<String, RespondTypeInfo>> cache = [:]
    private Map<String, String> currentImports = [:]

    Map<String, RespondTypeInfo> analyze(Class<?> controllerClass) {
        String key = controllerClass.name
        if (!cache.containsKey(key)) {
            cache[key] = doAnalyze(controllerClass)
        }
        return cache[key]
    }

    private Map<String, RespondTypeInfo> doAnalyze(Class<?> controllerClass) {
        File sourceFile = findSourceFile(controllerClass)
        if (!sourceFile?.exists()) return [:]

        currentImports = parseImports(sourceFile.text)

        List<ASTNode> nodes
        try {
            nodes = new AstBuilder().buildFromString(CompilePhase.CONVERSION, false, sourceFile.text)
        } catch (Exception ignored) {
            return [:]
        }

        ClassNode classNode = nodes.find {
            it instanceof ClassNode && it.nameWithoutPackage == controllerClass.simpleName
        }
        if (!classNode) return [:]

        Map<String, RespondTypeInfo> results = [:]
        classNode.methods.each { MethodNode method ->
            if (method.code instanceof BlockStatement) {
                RespondTypeInfo info = walkBlock((BlockStatement) method.code)
                if (info) results[method.name] = info
            }
        }
        return results
    }

    private File findSourceFile(Class<?> cls) {
        String relativePath = cls.name.replace('.', '/') + '.groovy'
        String userDir = System.getProperty('user.dir')
        for (String base : [
            "${userDir}/grails-app/controllers",
            "${userDir}/src/main/groovy",
        ]) {
            File f = new File("${base}/${relativePath}")
            if (f.exists()) return f
        }
        return null
    }

    private RespondTypeInfo walkBlock(BlockStatement block) {
        // varDecls: variable name → RHS expression (for def/untyped vars)
        // varTypes: variable name → declared simple type name (for explicitly typed vars)
        Map<String, Expression> varDecls = [:]
        Map<String, String> varTypes = [:]

        for (Statement s : block.statements) {
            if (s instanceof ExpressionStatement) {
                Expression e = ((ExpressionStatement) s).expression
                if (e instanceof DeclarationExpression) {
                    DeclarationExpression decl = (DeclarationExpression) e
                    if (decl.leftExpression instanceof VariableExpression) {
                        VariableExpression lv = (VariableExpression) decl.leftExpression
                        varDecls[lv.name] = decl.rightExpression
                        String declaredType = lv.type?.nameWithoutPackage
                        if (declaredType && !UNTYPED.contains(declaredType)) {
                            varTypes[lv.name] = declaredType
                        }
                    }
                }
            }

            RespondTypeInfo info = findRespondInStatement(s, varDecls, varTypes)
            if (info) return info
        }
        return null
    }

    private RespondTypeInfo findRespondInStatement(Statement stmt, Map<String, Expression> varDecls, Map<String, String> varTypes) {
        if (stmt instanceof ExpressionStatement) {
            return matchRespondCall(((ExpressionStatement) stmt).expression, varDecls, varTypes)
        }
        if (stmt instanceof ReturnStatement) {
            return matchRespondCall(((ReturnStatement) stmt).expression, varDecls, varTypes)
        }
        if (stmt instanceof IfStatement) {
            IfStatement ifStmt = (IfStatement) stmt
            RespondTypeInfo info = findRespondInBlock(ifStmt.ifBlock, varDecls, varTypes)
            if (info) return info
            if (ifStmt.elseBlock) return findRespondInBlock(ifStmt.elseBlock, varDecls, varTypes)
        }
        if (stmt instanceof TryCatchStatement) {
            return findRespondInBlock(((TryCatchStatement) stmt).tryStatement, varDecls, varTypes)
        }
        return null
    }

    private RespondTypeInfo findRespondInBlock(Statement stmt, Map<String, Expression> varDecls, Map<String, String> varTypes) {
        if (stmt instanceof BlockStatement) return walkBlock((BlockStatement) stmt)
        return findRespondInStatement(stmt, varDecls, varTypes)
    }

    private RespondTypeInfo matchRespondCall(Expression expr, Map<String, Expression> varDecls, Map<String, String> varTypes) {
        if (!(expr instanceof MethodCallExpression)) return null
        MethodCallExpression call = (MethodCallExpression) expr
        if (call.methodAsString != 'respond') return null

        List<Expression> args = extractArgs(call)
        if (!args) return null

        // respond([key: val]) or respond([:]) — Map literal, skip
        if (args[0] instanceof MapExpression) return null

        return resolveType(args[0], varDecls, varTypes)
    }

    private RespondTypeInfo resolveType(Expression expr, Map<String, Expression> varDecls, Map<String, String> varTypes) {
        // new SomeClass(...)
        if (expr instanceof ConstructorCallExpression) {
            String name = ((ConstructorCallExpression) expr).type.nameWithoutPackage
            Class<?> cls = resolveClass(name)
            if (cls) return new RespondTypeInfo(type: cls, isList: false)
        }

        // SomeClass.method(...) — static/GORM call on uppercase name
        if (expr instanceof MethodCallExpression) {
            MethodCallExpression call = (MethodCallExpression) expr
            String receiverName = upperCaseNameOf(call.objectExpression)
            if (receiverName) {
                Class<?> cls = resolveClass(receiverName)
                if (cls) {
                    boolean isList = GORM_LIST_METHODS.contains(call.methodAsString)
                        || isCollectionReturnMethod(cls, call.methodAsString)
                    return new RespondTypeInfo(type: cls, isList: isList)
                }
            }
        }

        // someVariable — check declared type first, then trace RHS
        if (expr instanceof VariableExpression) {
            String name = ((VariableExpression) expr).name

            // Declared type (e.g. PaginationResult publications = ...)
            String declaredType = varTypes[name]
            if (declaredType) {
                Class<?> cls = resolveClass(declaredType)
                if (cls) return new RespondTypeInfo(type: cls, isList: Collection.isAssignableFrom(cls))
            }

            // Fall back to RHS expression
            Expression rhs = varDecls[name]
            if (rhs) return resolveType(rhs, varDecls, varTypes)
        }

        return null
    }

    private static String upperCaseNameOf(Expression expr) {
        if (expr instanceof VariableExpression) {
            String name = ((VariableExpression) expr).name
            if (name && Character.isUpperCase(name.charAt(0))) return name
        }
        return null
    }

    private static boolean isCollectionReturnMethod(Class<?> cls, String methodName) {
        try {
            return cls.methods.any { it.name == methodName && Collection.isAssignableFrom(it.returnType) }
        } catch (Exception ignored) {
            return false
        }
    }

    private Class<?> resolveClass(String simpleName) {
        if (!simpleName || UNTYPED.contains(simpleName)) return null

        try {
            def dc = grailsApplication?.getArtefact('Domain', simpleName)
            if (dc) return dc.clazz
        } catch (Exception ignored) {}

        try {
            Class<?> found = grailsApplication?.allClasses?.find { it.simpleName == simpleName }
            if (found) return found
        } catch (Exception ignored) {}

        ClassLoader cl = Thread.currentThread().contextClassLoader

        String fullName = currentImports[simpleName]
        if (fullName) {
            try { return cl.loadClass(fullName) } catch (ClassNotFoundException ignored) {}
        }

        try {
            return cl.loadClass(simpleName)
        } catch (ClassNotFoundException ignored) {}

        return null
    }

    static Map<String, String> parseImports(String source) {
        Map<String, String> imports = [:]
        source.eachLine { String line ->
            def m = line.trim() =~ /^import\s+([\w.]+?)(\s+as\s+(\w+))?$/
            if (m.matches()) {
                String fullName = m[0][1]
                String alias = m[0][3] ?: fullName.tokenize('.').last()
                imports[alias] = fullName
            }
        }
        return imports
    }

    private static List<Expression> extractArgs(MethodCallExpression call) {
        if (call.arguments instanceof ArgumentListExpression) {
            return ((ArgumentListExpression) call.arguments).expressions
        }
        if (call.arguments instanceof TupleExpression) {
            return ((TupleExpression) call.arguments).expressions
        }
        return []
    }
}
