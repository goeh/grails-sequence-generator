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
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.jmx.export.annotation.ManagedAttribute
import org.springframework.jmx.export.annotation.ManagedOperation
import org.springframework.jmx.export.annotation.ManagedResource

import javax.management.MBeanServer
import java.util.concurrent.ConcurrentHashMap

/**
 * A service that provide sequence counters (for customer numbers, invoice numbers, etc)
 * This service has two primary methods: nextNumber() and nextNumberLong().
 */
@ManagedResource(description = "Grails Sequence Generator")
class SequenceGeneratorService {

    static transactional = false

    def grailsApplication

    private static final Map<String, SequenceHandle> activeSequences = new ConcurrentHashMap<String, SequenceHandle>()

    boolean keepGoing
    boolean persisterRunning
    private Thread persisterThread

    private void initPersister() {
        if (persisterThread == null) {
            synchronized (activeSequences) {
                if (persisterThread == null) {
                    def interval = 1000 * (grailsApplication.config.sequence.flushInterval ?: 60)
                    persisterThread = new Thread("GrailsSequenceGenerator")
                    persisterThread.start {
                        persisterRunning = true
                        keepGoing = true
                        log.info "Sequence persister thread started with [$interval ms] flush interval"
                        while (keepGoing) {
                            try {
                                Thread.currentThread().sleep(interval)
                                log.trace("Scheduled flush")
                                synchronized (activeSequences) {
                                    flush()
                                }
                            } catch (InterruptedException e) {
                                log.info("Sequence flusher thread interrupted")
                                synchronized (activeSequences) {
                                    flush()
                                }
                            } catch (Exception e) {
                                if (log != null) {
                                    log.error "Failed to flush sequences to database!", e
                                } else {
                                    e.printStackTrace()
                                }
                            }
                        }
                        persisterRunning = false
                        log.info "Sequence persister thread stopped"
                    }

                    Runtime.runtime.addShutdownHook {
                        terminate()
                    }
                }
            }
        }
    }

    @CompileStatic
    SequenceHandle initSequence(Class clazz, String group = null, Long tenant = null, Long start = null, String format = null) {
        doInitSequence(clazz.simpleName, group, tenant, start, format)
    }

    @CompileStatic
    SequenceHandle initSequence(String name, String group = null, Long tenant = null, Long start = null, String format = null) {
        doInitSequence(name, group, tenant, start, format)
    }

    private SequenceHandle doInitSequence(String name, String group, Long tenant, Long start, String format) {
        def key = generateKey(name, group, tenant)
        def h = activeSequences.get(key)
        if (h == null) {
            synchronized (activeSequences) {
                h = activeSequences.get(key)
                if (h == null) {
                    def seq = findNumber(name, group, tenant)
                    if (seq != null) {
                        h = seq.toHandle()
                        log.debug "Loaded existing sequence [$key] starting at [${h.number}] with format [${h.format}]"
                    } else {
                        def config = grailsApplication.config.sequence
                        if (start == null) {
                            start = config."$name".initPersister ?: 1L
                        }
                        if (!format) {
                            format = config."$name".format ?: '%d'
                        }
                        h = createHandle(start, format)
                        log.debug "Created new sequence [$key] starting at [${h.number}] with format [${h.format}]"
                    }
                    activeSequences.put(key, h)
                }
            }
            initPersister()
        }
        return h
    }

    @CompileStatic
    private String generateKey(final String name, final String group, final Long tenant) {
        final StringBuilder s = new StringBuilder()
        if (name != null) {
            s.append(name)
        }
        s.append('/')
        if (group != null) {
            s.append(group)
        }
        s.append('/')
        if (tenant != null) {
            s.append(tenant.toString())
        }
        return s.toString()
    }

    private SequenceHandle getHandle(String name, String group = null, Long tenant = null) {
        def key = generateKey(name, group, tenant)
        def h = activeSequences.get(key)
        if (h == null) {
            def seq = findNumber(name, group, tenant)
            if (seq != null) {
                h = seq.toHandle()
                log.debug "Loaded existing sequence [$key] starting at [${h.number}] with format [${h.format}]"
            } else {
                def config = grailsApplication.config.sequence
                def start = config."$name".initPersister ?: 1L
                def format = config."$name".format ?: '%d'
                h = createHandle(start, format)
                log.debug "Created new sequence [$key] starting at [${h.number}] with format [${h.format}]"
            }
            synchronized (activeSequences) {
                SequenceHandle tmp = activeSequences.get(key)
                if (tmp != null) {
                    h = tmp
                } else {
                    activeSequences.put(key, h)
                }
            }
            initPersister()
        }

        return h
    }

    @CompileStatic
    private SequenceHandle createHandle(final Long start, final String format) {
        new SequenceHandle(start, format)
    }

    @CompileStatic
    String nextNumber(Class clazz, String group = null, Long tenant = null) {
        SequenceHandle h = initSequence(clazz.simpleName, group, tenant)
        synchronized (h) {
            return h.nextFormatted()
        }
    }

    @CompileStatic
    String nextNumber(String name, String group = null, Long tenant = null) {
        SequenceHandle h = initSequence(name, group, tenant)
        synchronized (h) {
            return h.nextFormatted()
        }
    }

    @CompileStatic
    Long nextNumberLong(String name, String group = null, Long tenant = null) {
        SequenceHandle h = initSequence(name, group, tenant)
        synchronized (h) {
            return h.next()
        }
    }

    @CompileStatic
    boolean setNextNumber(Long currentNumber, Long newNumber, String name, String group = null, Long tenant = null) {
        def rval = false
        if (currentNumber != newNumber) {
            SequenceHandle h = getHandle(name, group, tenant)
            if (h.number == currentNumber) {
                synchronized (h) {
                    if (h.number == currentNumber) {
                        h.number = newNumber
                        rval = true
                        log.debug "Sequence [$currentNumber] changed to [$newNumber]"
                    }
                }
            }
        }
        rval
    }

    Long refresh(Class clazz, String group = null, Long tenant = null) {
        refresh(clazz.simpleName, group, tenant)
    }

    Long refresh(String name, String group = null, Long tenant = null) {
        SequenceNumber n = findNumber(name, group, tenant)
        Long number
        if (n) {
            SequenceHandle h = getHandle(name, group, tenant)
            if (h) {
                synchronized (h) {
                    if (h.dirty) {
                        number = h.number
                    } else {
                        SequenceNumber.withTransaction {
                            n = SequenceNumber.lock(n.id)
                            number = n.number
                            h.setNumber(number)
                        }
                    }
                }
            }
        }
        return number
    }

    @CompileStatic
    private void terminate() {
        keepGoing = false
        Thread t = persisterThread
        if (t != null) {
            synchronized (t) {
                if (persisterThread != null) {
                    persisterThread = null
                    t.interrupt()
                    try {
                        t.join(5000L)
                    } catch (InterruptedException e) {
                        log.error("Error shutting down persister thread", e)
                    } catch (NullPointerException e) {
                        log.error("Persister thread was already terminated", e)
                    } finally {
                        log.debug "Sequence generator terminated"
                        if (!activeSequences.isEmpty()) {
                            log.debug "Active sequences: $activeSequences"
                            activeSequences.clear()
                        }
                    }
                }
            }
        }
    }

    @CompileStatic
    synchronized void shutdown() {
        keepGoing = false
        try {
            synchronized (activeSequences) {
                flush()
            }
        } catch (Exception e) {
            log.error "Failed to save sequence counters!", e
        }
        terminate()
    }

    private Collection<String> getDirtySequences() {
        activeSequences.findAll { it.value.dirty }.keySet()
    }

    private void flush() {
        def dirtyKeys = getDirtySequences()
        if (dirtyKeys) {
            if (log.isDebugEnabled()) {
                log.debug("Saving dirty sequences: $dirtyKeys")
            }
        } else if (log.isTraceEnabled()) {
            log.trace("All sequences are clean")
        }

        for (String key in dirtyKeys) {
            List<String> parts = key.split('/').toList()
            String name = parts[0]
            String group = parts[1] ?: null
            Long tenant = parts[2] ? Long.valueOf(parts[2]) : null
            SequenceHandle handle = activeSequences.get(key)
            synchronized (handle) {
                Long n = handle.number
                SequenceDefinition.withTransaction { tx ->
                    SequenceNumber seq = findNumber(name, group, tenant)
                    if (seq) {
                        int counter = 10
                        while (counter--) {
                            try {
                                seq = SequenceNumber.lock(seq.id)
                                counter = 0
                            } catch (OptimisticLockingFailureException e) {
                                log.warn "SequenceNumber locked, retrying..."
                                Thread.currentThread().sleep(25)
                            }
                        }
                        seq.number = n
                        seq.save(failOnError: true)
                    } else {
                        SequenceDefinition d = findDefinition(name, tenant)
                        if (d) {
                            d.addToNumbers(group: group, number: n)
                        } else {
                            d = new SequenceDefinition(tenantId: tenant, name: name, format: handle.format)
                            d.addToNumbers(group: group, number: n)
                        }
                        d.save(failOnError: true)
                    }
                }
                handle.dirty = false
                if (log.isTraceEnabled()) {
                    log.trace "Saved sequence $key = $n"
                }
            }
        }
    }

    private SequenceDefinition findDefinition(final String name, final Long tenant) {
        SequenceDefinition.createCriteria().get {
            eq('name', name)
            if (tenant != null) {
                eq('tenantId', tenant)
            } else {
                isNull('tenantId')
            }
            cache true
        }
    }

    private SequenceNumber findNumber(final String name, final String group, final Long tenant) {
        SequenceNumber.createCriteria().get {
            definition {
                eq('name', name)
                if (tenant != null) {
                    eq('tenantId', tenant)
                } else {
                    isNull('tenantId')
                }
            }
            if (group != null) {
                eq('group', group)
            } else {
                isNull('group')
            }
        }
    }

    List<Map> statistics(Long tenant = null) {
        synchronized (activeSequences) {
            flush()
        }
        final List<SequenceNumber> numbers = SequenceNumber.createCriteria().list() {
            definition {
                if (tenant != null) {
                    eq('tenantId', tenant)
                } else {
                    isNull('tenantId')
                }
                order 'name'
            }
            order 'group'
        }
        final List<Map> result = []
        for (SequenceNumber n in numbers) {
            SequenceDefinition d = n.definition
            String key = generateKey(d.name, n.group, d.tenantId)
            SequenceHandle handle = activeSequences[key]
            if (handle) {
                result << [name: d.name, format: d.format, number: handle.getNumber()]
            }
        }
        result
    }

    @ManagedAttribute(description = "Sequence generator statistics")
    String getStatistics() {
        SequenceNumber.withTransaction {
            statistics().collect { "${it.name}=${it.number}" }.join(', ')
        }
    }

    @ManagedOperation(description = "Save dirty sequences to disc")
    void sync() {
        SequenceNumber.withTransaction {
            synchronized (activeSequences) {
                flush()
            }
        }
    }
}
