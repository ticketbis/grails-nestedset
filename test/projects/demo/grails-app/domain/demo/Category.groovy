package demo

import com.ticketbis.nestedset.ast.Nestedset
import grails.compiler.GrailsCompileStatic

@GrailsCompileStatic
@Nestedset
class Category {
    String name

    static constraints = {

    }

    static mapping = {
    }

    static namedQueries = {
    }

}
