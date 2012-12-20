package grails.plugins.sequence

import grails.converters.JSON

import javax.servlet.http.HttpServletResponse

/**
 * Admin actions for the sequence generator.
 */
class SequenceGeneratorController {

    def sequenceGeneratorService
    def currentTenant

    static allowedMethods = [update: 'POST']

    def list(String name, String group) {
        def tenant = currentTenant?.get()?.longValue()
        def result = sequenceGeneratorService.statistics(tenant)
        if (name) {
            result = result.findAll { it.name == name }
        }
        if (group) {
            result = result.findAll { it.group == group }
        }
        render result as JSON
    }

    def update(String name, String group, Long current, Long next) {
        def tenant = currentTenant?.get()?.longValue()
        def rval = [:]
        if (sequenceGeneratorService.setNextNumber(current, next, name, group, tenant)) {
            rval.success = true
            rval.message = 'Sequence number updated'
        } else {
            rval.success = false
            rval.message = 'Current number was not current, please try again.'
            //response.sendError(HttpServletResponse.SC_BAD_REQUEST)
        }
        render rval as JSON
    }
}
