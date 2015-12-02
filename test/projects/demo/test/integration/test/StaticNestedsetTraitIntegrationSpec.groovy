package test

import demo.*
import grails.test.spock.IntegrationSpec
import spock.lang.Shared
import com.ticketbis.nestedset.NestedsetTrait
import com.ticketbis.nestedset.NestedsetException

class StaticNestedsetTraitIntegrationSpec extends IntegrationSpec {
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

    void "test roots"() {
        expect:
            Category.roots.size() == 2
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

    void "test moveNode"() {
        given:
            Category.moveNode(category, parent2)
            parent.refresh()
        expect:
            category.lft == 4
            category.rgt == 5
            parent2.rgt == 10
            parent.lft == 1
            parent.rgt == 2
    }

    void "test moveNode exception when moving to its children"() {
        when:
            Category.moveNode(parent2, category2)
        then:
            thrown NestedsetException
    }

    void "test save method protected for inserts"() {
        when:
            Category badCategory = new Category(name: "Bad category")
            badCategory.save(flush: true)
        then:
            thrown NestedsetException
    }

    void "test save method protected for updates"() {
        when:
            category.refresh()
            category.lft = 18
            println "before save ${category.__nestedsetMutable}"
            category.save(flush:true)
            println "after save ${category.__nestedsetMutable}"
        then:
            thrown NestedsetException
    }
}
