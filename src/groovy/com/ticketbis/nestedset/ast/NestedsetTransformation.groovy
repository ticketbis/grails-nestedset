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

        addFields(targetClassNode)
        addNestedsetTrait(targetClassNode, sourceUnit)
    }

    private void addFields(ClassNode classNode) {
        ['lft', 'rgt', 'depth'].each { field ->
            NestedsetASTUtils.getOrCreateField(
                classNode,
                field,
                new ConstantExpression(0),
                FieldNode.ACC_PUBLIC,
                Integer)
        }

        // parent node
        //def classNodeLnk = ClassHelper.make(classNode.name)
        //classNodeLnk.setRedirect(classNode)
        def classNodeLabel = ClassHelper.makeWithoutCaching(classNode.name)
        NestedsetASTUtils.getOrCreateField(
            classNode,
            'parent',
            new EmptyExpression(),
            FieldNode.ACC_PUBLIC,
            classNodeLabel
        )
    }

    private void addNestedsetTrait(ClassNode classNode, SourceUnit sourceUnit) {
        if (classNode.declaresInterface(NESTEDSET_NODE))
            return

        classNode.addInterface(NESTEDSET_NODE)
        TraitComposer.doExtendTraits(classNode, sourceUnit, compilationUnit)

        //NestedsetASTUtils.addTransient(classNode, 'translationsMapCache')
        //NestedsetASTUtils.addTransient(classNode, 'translationByLocale')
    }
}

