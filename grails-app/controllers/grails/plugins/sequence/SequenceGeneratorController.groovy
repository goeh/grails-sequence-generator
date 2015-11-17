/*
 * Copyright (c) 2015 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * under the License.
 */

package grails.plugins.sequence

import grails.converters.JSON

/**
 * Admin actions for the sequence generator.
 */
class SequenceGeneratorController {

    def sequenceGeneratorService
    def currentTenant

    static allowedMethods = [update: 'POST']

    def index(String name, String group, Long current, Long next) {
        def tenant = currentTenant?.get()?.longValue()
        if (request.post) {
            if (sequenceGeneratorService.setNextNumber(current, next, name, group, tenant)) {
                flash.message = message(code: 'sequenceGenerator.update.message', default: "Sequence updated", args: [name, group, current, next])
            } else {
                flash.message = message(code: 'sequenceGenerator.update.error', default: "Sequence could not be updated", args: [name, group, current, next])
            }
        }
        [result: sequenceGeneratorService.statistics(tenant)]
    }

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
        }
        render rval as JSON
    }
}
