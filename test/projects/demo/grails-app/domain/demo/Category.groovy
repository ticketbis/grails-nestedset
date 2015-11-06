package demo

import com.ticketbis.nestedset.ast.Nestedset
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@Nestedset
class Category {
    String name

    Date lastUpdated

    static constraints = {

    }

    static mapping = {
    }

    static namedQueries = {
    }

}
