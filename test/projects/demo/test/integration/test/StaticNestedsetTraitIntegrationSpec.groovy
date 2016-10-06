package test

import demo.*
import grails.test.spock.IntegrationSpec
import com.ticketbis.nestedset.NestedsetException

class StaticNestedsetTraitIntegrationSpec extends IntegrationSpec {

    Category parent = new Category(name: "Root")
    Category parent2 = new Category(name: "Root 2")
    Category category = new Category(name: "Leven 1A", parent: parent)
    Category category2 = new Category(name: "Level 1B", parent: parent2)
    Category category3 = new Category(name: "Level 1C", parent: parent2)
    Category category4 = new Category(name: "Level 2A", parent: category2)

    def setup() {
        Category.addNode(parent)
        parent.save()
        Category.addNode(category)
        category.save()

        Category.addNode(parent2)
        parent2.save()
        Category.addNode(category2, parent2)
        category2.save()
        Category.addNode(category4, category2)
        category4.save()
        Category.addNode(category3, parent2)
        category3.save()
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
            category4.lft == 7
            category4.rgt == 8
            category2.rgt == 9
            category3.lft == 10
            category3.rgt == 11
            parent2.rgt == 12

            parent.depth == 1
            category.depth == 2
            parent2.depth == 1
            category2.depth == 2
            category3.depth == 2
            category4.depth == 3
    }

    void "test moveNode"() {
        given:
            Category.moveNode(category2, parent)
            parent2.refresh()
            category.refresh()
            category4.refresh()

        expect:
            category2.lft == 2
            category4.lft == 3
            category4.rgt == 4
            category2.rgt == 5
            category.lft == 6
            category.rgt == 7
            parent.rgt == 8

            parent2.lft == 9
            parent2.rgt == 12

    }

    void "test moveNode should only update the category subtree's lastUpdated field"() {
        when:
            Category.moveNode(category2, parent)
            category4.refresh()
            category.refresh()
            parent2.refresh()
            category3.refresh()

        then:
            category2.lastUpdated > old(category2.lastUpdated)
            category4.lastUpdated > old(category4.lastUpdated)

            parent.lastUpdated == old(parent.lastUpdated)
            parent2.lastUpdated == old(parent2.lastUpdated)
            category.lastUpdated == old(category.lastUpdated)
            category3.lastUpdated == old(category3.lastUpdated)
    }

    void "test moveNode exception when moving to its children"() {
        when:
            Category.moveNode(parent2, category2)
        then:
            thrown NestedsetException
    }

    void "test save method protected for inserts"() {
        setup:
            Category badCategory = new Category(name: "Bad category")
        when:
            badCategory.save(flush: true)
        then:
            thrown NestedsetException
    }

    void "test save method protected for updates"() {
        setup:
            category.lft = 18
        when:
            category.save(flush: true)
        then:
            thrown NestedsetException
    }

}
