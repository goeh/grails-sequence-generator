import grails.plugins.sequence.SequenceEntity

class SequenceGeneratorGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.3 > *"
    // the other plugins this plugin depends on
    def dependsOn = [:]
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/domain/grails/plugins/sequence/SequenceTestEntity.groovy"
    ]

    def title = "Sequence Number Generator" // Headline display name of the plugin
    def author = "Goran Ehrsson"
    def authorEmail = "goran@technipelago.se"
    def description = '''
A service that generate sequence numbers from different sequences, formats, etc.
The method getNextSequenceNumber() is injected into all domain classes. It returns
the next number for the sequence defined for the domain.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/sequence"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"

    // Details of company behind the plugin (if there is one)
    def organization = [name: "Technipelago AB", url: "http://www.technipelago.se/"]

    // Any additional developers beyond the author specified above.
    //    def developers = [ [ name: "Joe Bloggs", email: "joe@bloggs.net" ]]

    // Location of the plugin's issue tracker.
    def issueManagement = [ system: "JIRA", url: "http://jira.grails.org/browse/GPSEQUENCE" ]

    // Online location of the plugin's browseable source code.
    def scm = [ url: "https://github.com/goeh/grails-sequence" ]

    def doWithWebDescriptor = { xml ->
        // TODO Implement additions to web.xml (optional), this event occurs before
    }

    def doWithSpring = {
        // TODO Implement runtime spring config (optional)
    }

    def doWithDynamicMethods = { ctx ->
        // TODO Implement registering dynamic methods to classes (optional)
    }

    def doWithApplicationContext = { applicationContext ->
        def config = application.config
        for (c in application.domainClasses) {
            if(c.clazz.getAnnotation(SequenceEntity)) {
                addDomainMethods(applicationContext, config, c.metaClass)
            }
        }
    }

    def onChange = { event ->
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    def onConfigChange = { event ->
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    def onShutdown = { event ->
        // TODO Implement code that is executed when the application shuts down (optional)
    }

    private void addDomainMethods(ctx, config, MetaClass mc) {
        def service = ctx.getBean('sequenceGeneratorService')
        mc.getNextSequenceNumber = {group = null ->
            def name = delegate.class.simpleName
            def tenant = delegate.hasProperty('tenantId') ? delegate.tenantId : null
            def nbr
            delegate.class.withNewSession {
                nbr = service.nextNumber(name, group, tenant)
            }
            return nbr
        }
    }
}
