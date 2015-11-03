package test

import demo.*
import grails.test.spock.IntegrationSpec
import com.ticketbis.nestedset.NestedsetTrait

class NestedsetTraitIntegrationSpec extends IntegrationSpec {

    def setup() {
    }

    def cleanup() {
    }

    void "test isRoot"() {
        given:
            def parent = new Category(name: "Dummy parent category")
            def category = new Category(name: "Dummy category", parent: parent)
        expect:
            category.rootNode == false
            parent.rootNode == true
    }

    void "test isLeaf"() {
        given:
            def parent = new Category(name: "Dummy parent category")
            def category = new Category(name: "Dummy category", parent: parent)
            //Category.addChild(category, parent)
            parent.save()
        expect:
            category.leaf == true
            parent.leaf == false
    }
}
