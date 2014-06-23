/*
 * Copyright (c) 2012 Goran Ehrsson.
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

import groovy.transform.CompileStatic
import org.springframework.jmx.export.annotation.ManagedAttribute
import org.springframework.jmx.export.annotation.ManagedOperation
import org.springframework.jmx.export.annotation.ManagedResource

/**
 * A service that provide sequence counters (for customer numbers, invoice numbers, etc)
 * This service has two primary methods: nextNumber() and nextNumberLong().
 */
@ManagedResource(description = "Grails Sequence Generator")
class SequenceGeneratorService {

    static transactional = false

    SequenceGenerator sequenceGenerator

    @CompileStatic
    grails.plugins.sequence.Sequence initSequence(Class clazz, String group = null, Long tenant = null, Long start = null, String format = null) {
        initSequence(clazz.simpleName, group, tenant, start, format)
    }

    @CompileStatic
    grails.plugins.sequence.Sequence initSequence(String name, String group = null, Long tenant = null, Long start = null, String format = null) {
        sequenceGenerator.createSequence(tenant ?: 0L, name, group, format, start != null ? start : 1L)
    }

    @CompileStatic
    String nextNumber(Class clazz, String group = null, Long tenant = null) {
        nextNumber(clazz.simpleName, group, tenant)
    }

    @CompileStatic
    String nextNumber(String name, String group = null, Long tenant = null) {
        grails.plugins.sequence.Sequence h = initSequence(name, group, tenant)
        synchronized (h) {
            return h.nextFormatted()
        }
    }

    @CompileStatic
    Long nextNumberLong(String name, String group = null, Long tenant = null) {
        grails.plugins.sequence.Sequence h = initSequence(name, group, tenant)
        synchronized (h) {
            return h.next()
        }
    }

    @CompileStatic
    boolean setNextNumber(Long currentNumber, Long newNumber, String name, String group = null, Long tenant = null) {
        if (currentNumber != newNumber) {
            if(sequenceGenerator.update(tenant ?: 0L, name, group, null, currentNumber, newNumber)) {
                log.debug "Sequence [$name] in tenant [$tenant] changed from [$currentNumber] to [$newNumber]"
                return true
            }
        }
        return false
    }

    Iterable<SequenceStatus> statistics(Long tenant = null) {
        sequenceGenerator.getStatistics(tenant ?: 0L)
    }

    @ManagedAttribute(description = "Sequence generator statistics")
    String getStatistics() {
        statistics().collect { "${it.name}=${it.number}" }.join(', ')
    }

}
