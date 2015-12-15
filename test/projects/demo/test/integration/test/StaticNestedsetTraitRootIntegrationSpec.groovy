package test

import demo.*
import grails.test.spock.IntegrationSpec
import spock.lang.Shared
import com.ticketbis.nestedset.NestedsetTrait
import com.ticketbis.nestedset.NestedsetException

class StaticNestedsetTraitRootIntegrationSpec extends IntegrationSpec {
    @Shared parent = new Category(name: "Root")
    @Shared parent2 = new Category(name: "Root 2")
    @Shared category = new Category(name: "Leven 1A", parent: parent)
    @Shared category2 = new Category(name: "Level 1B")
    @Shared category3 = new Category(name: "Level 2B")

    def setupSpec() {
        Category.addNode(parent)
        Category.addNode(category)

        Category.addNode(parent2)
        Category.addNode(category2, parent2)
        Category.addNode(category3, parent2)
    }

    def cleanupSpec() {
        Category.deleteNode(category3)
        Category.deleteNode(category2)
        Category.deleteNode(category)
        Category.deleteNode(parent)
        Category.deleteNode(parent2)
    }

    void "test moveNode to root"() {
        given:
            category.refresh()
            Category.moveNode(category, null)
            parent.refresh()
            parent2.refresh()
        expect:
            parent.depth == 1
            parent2.depth == 1
            category.depth == 1

            parent.lft == 1
            parent.rgt == 2

            parent2.lft == 3
            parent2.rgt == 8

            category.lft == 9
            category.rgt == 10
    }
}
