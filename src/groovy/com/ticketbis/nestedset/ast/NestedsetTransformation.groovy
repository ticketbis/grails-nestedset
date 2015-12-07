package com.ticketbis.nestedset.ast

import groovy.util.logging.Log4j
import groovy.transform.CompilationUnitAware
import org.codehaus.groovy.transform.ASTTransformation
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.compiler.injection.GrailsASTUtils
import org.codehaus.groovy.ast.builder.AstBuilder

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.transform.trait.TraitComposer
import org.codehaus.groovy.ast.tools.GenericsUtils
import java.lang.reflect.Modifier

import com.ticketbis.nestedset.NestedsetTrait

@Log4j
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
class NestedsetTransformation implements ASTTransformation, CompilationUnitAware {

    CompilationUnit compilationUnit

    private static final ClassNode NESTEDSET_NODE = new ClassNode(NestedsetTrait)

    void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        assert astNodes[0] instanceof AnnotationNode
        assert astNodes[1] instanceof ClassNode,
                "@Nestedset can only be applied on classes"

        AnnotationNode annotation = astNodes[0]
        ClassNode targetClassNode = astNodes[1]

        assert GrailsASTUtils.isDomainClass(targetClassNode, sourceUnit),
                "@Nestedset annotation should be applied over domain classes"

        log.info "Adding Nestedset transform to ${ targetClassNode.name }..."

        addProperties(targetClassNode)
        addNestedsetTrait(targetClassNode, sourceUnit)
        addConstraints(targetClassNode)
        addbeforeInsertHook(targetClassNode)
        addbeforeUpdateHook(targetClassNode)
    }

    private void addProperties(ClassNode classNode) {
        ['lft', 'rgt', 'depth'].each { field ->
            NestedsetASTUtils.getOrCreateProperty(
                classNode,
                field,
                new ConstantExpression(0),
                Modifier.PUBLIC,
                ClassHelper.Integer_TYPE)
        }

        // parent node
        //def classNodeLnk = ClassHelper.make(classNode.name)
        //classNodeLnk.setRedirect(classNode)
        def classNodeLabel = ClassHelper.makeWithoutCaching(classNode.name)
        NestedsetASTUtils.getOrCreateProperty(
            classNode,
            'parent',
            new EmptyExpression(),
            Modifier.PUBLIC,
            classNodeLabel
        )

        // transient property to avoid manual nestedset properties manipulation
        NestedsetASTUtils.getOrCreateProperty(
            classNode,
            'nestedsetMutable',
            new ConstantExpression(false),
            Modifier.PUBLIC,
              ClassHelper.Boolean_TYPE)
        NestedsetASTUtils.addTransient(classNode, 'nestedsetMutable')
    }

    private void addNestedsetTrait(ClassNode classNode, SourceUnit sourceUnit) {
        if (classNode.declaresInterface(NESTEDSET_NODE))
            return

        classNode.addInterface(NESTEDSET_NODE)
        TraitComposer.doExtendTraits(classNode, sourceUnit, compilationUnit)
    }

    private void addConstraints(ClassNode classNode) {
        NestedsetASTUtils.addSettings(
            'constraints',
            classNode,
            'parent',
            'nullable: true'
        )
    }

    private void addbeforeInsertHook(ClassNode classNode) {
        MethodNode methodNode = classNode.getMethod("beforeInsert", Parameter.EMPTY_ARRAY)
        if (!methodNode) {
            methodNode = classNode.addMethod("beforeInsert",
                    Modifier.PUBLIC,
                    ClassHelper.OBJECT_TYPE,
                    Parameter.EMPTY_ARRAY,
                    null,
                    new BlockStatement());
        }

        BlockStatement statement = new BlockStatement([
              new ExpressionStatement(
                  new MethodCallExpression(
                      VariableExpression.THIS_EXPRESSION,
                      'b4Insert',
                      MethodCallExpression.NO_ARGUMENTS
                  )
              )
          ] as Statement[],
          new VariableScope())

        methodNode.code.addStatement(statement)
    }

    private void addbeforeUpdateHook(ClassNode classNode) {
        MethodNode methodNode = classNode.getMethod("beforeUpdate", Parameter.EMPTY_ARRAY)
        if (!methodNode) {
            methodNode = classNode.addMethod("beforeUpdate",
                    Modifier.PUBLIC,
                    ClassHelper.OBJECT_TYPE,
                    Parameter.EMPTY_ARRAY,
                    null,
                    new BlockStatement());
        }

        BlockStatement statement = new BlockStatement([
              new ExpressionStatement(
                  new MethodCallExpression(
                      VariableExpression.THIS_EXPRESSION,
                      'b4Update',
                      MethodCallExpression.NO_ARGUMENTS
                  )
              )
          ] as Statement[],
          new VariableScope())

        methodNode.code.addStatement(statement)
    }
}

