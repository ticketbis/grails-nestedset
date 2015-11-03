package test

import demo.*
import grails.test.spock.IntegrationSpec
import com.ticketbis.nestedset.NestedsetTrait

class NestedsetASTIntegrationSpec extends IntegrationSpec {

    def setup() {
    }

    def cleanup() {
    }

    void "test Nestedset trait"() {
        given:
            def category = new Category(name: "Dummy category")
        expect:
            category instanceof NestedsetTrait
    }

    void "test domain fields injected"() {
        given:
            def parent = new Category(name: "Dummy parent category")
            def category = new Category(name: "Dummy category", parent: parent)
        expect:
            category.lft == 0
            category.rgt == 0
            category.depth == 0
            category.parent instanceof Category
    }

}
