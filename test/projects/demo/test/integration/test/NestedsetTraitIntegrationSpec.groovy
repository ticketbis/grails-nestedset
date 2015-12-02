package test

import demo.*
import grails.test.spock.IntegrationSpec
import spock.lang.Shared
import com.ticketbis.nestedset.NestedsetTrait

class NestedsetTraitIntegrationSpec extends IntegrationSpec {
    @Shared parent = new Category(name: "Root")
    @Shared category = new Category(name: "Leven 1A", parent: parent)
    @Shared category2 = new Category(name: "Level 1B")
    @Shared category3 = new Category(name: "Level 2B")

    def setupSpec() {
        Category.addNode(parent)
        Category.addNode(category)
        Category.addNode(category2, parent)
        Category.addNode(category3, category2)
        parent.refresh()
    }

    def cleanupSpec() {
        Category.deleteNode(category3)
        Category.deleteNode(category2)
        Category.deleteNode(category)
        Category.deleteNode(parent)
    }

    void "test isRootNode"() {
        expect:
            category.rootNode == false
            parent.rootNode == true
    }

    void "test isLeaf"() {
        expect:
            category.leaf == true
            parent.leaf == false
    }

    void "test descendants"() {
        expect:
            parent.descendants.size() == 3
            category.descendants.size() == 0
            category2.descendants.size() == 1

            parent.descendants[0].id == category.id
            parent.descendants[1].id == category2.id
            parent.descendants[2].id == category3.id

            parent.countDescendants() == 3

            category.countDescendants() == 0
            category2.countDescendants() == 1
    }

    void "test ancestors"() {
        expect:
            parent.ancestors == []
            category.ancestors.collect{it.id} == [parent.id]
            category2.ancestors.collect{it.id} == [parent.id]
            category3.ancestors.collect{it.id} == [parent.id, category2.id]
    }

    void "test leafs"() {
        expect:
            parent.leafs.collect{it.id} == [category.id, category3.id]
            category.leafs == []
            category2.leafs.collect{it.id} == [category3.id]
            category3.leafs == []
    }

    void "test children"() {
        expect:
            parent.children.collect{it.id} == [category.id, category2.id]
            category.children == []
            category2.children.collect{it.id} == [category3.id]
            category3.children == []

            parent.countChildren() == 2
    }

    void "test lastChild"() {
        expect:
            parent.lastChild.id == category2.id
    }

    void "test getRoot"() {
        expect:
            parent.root == null
            category3.root.id == parent.id
    }

    void "test isDescendant"() {
        expect:
            category.isDescendant(parent) == true
            parent.isDescendant(category) == false
    }

}
