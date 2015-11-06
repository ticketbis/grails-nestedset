package test

import demo.*
import grails.test.spock.IntegrationSpec
import spock.lang.Shared
import com.ticketbis.nestedset.NestedsetTrait

class StaticNestedsetTraitIntegrationSpec extends IntegrationSpec {
    @Shared parent = new Category(name: "Root")
    @Shared category = new Category(name: "Leven 1A", parent: parent)
    @Shared category2 = new Category(name: "Level 1B")
    @Shared category3 = new Category(name: "Level 2B")
    
    def setupSpec() {
        Category.addNode(parent)
        Category.addNode(category)
        Category.addNode(category2, parent)
        Category.addNode(category3, category2)
    }

    def cleanupSpec() {
    }

    void "test addNode"() {
        expect:
            parent.lft == 1
            category.lft == 2
            category.rgt == 3
            parent.rgt == 4

            parent2.lft == 5
            category2.lft == 6
            category2.rgt == 7
            category3.lft == 8
            category3.rgt == 9
            parent2.rgt == 10
    }
}
