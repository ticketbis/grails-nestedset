class NestedsetGrailsPlugin {
    // the plugin version
    def version = "0.1.3"
    def groupId = "com.ticketbis"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "2.4 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    def title = "Nestedset Plugin"
    def author = "Jose Gargallo"
    def authorEmail = ""
    def description = '''\
The nested set model is a particular technique for representing nested sets (also known as trees or hierarchies) in relational databases. This plugin
provides nestedset behaviour to domain classes.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/nestedset"

    def license = "APACHE"
    def organization = [ name: "Ticketbis", url: "http://engineering.ticketbis.com/" ]

    // Any additional developers beyond the author specified above.
//    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
//    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPMYPLUGIN" ]

    def scm = [ url: "https://github.com/ticketbis/grails-nestedset" ]

    def doWithWebDescriptor = { xml ->
    }

    def doWithSpring = {
    }

    def doWithDynamicMethods = { ctx ->
    }

    def doWithApplicationContext = { applicationContext ->
    }

    def onChange = { event ->
    }

    def onConfigChange = { event ->
    }

    def onShutdown = { event ->
    }
}
