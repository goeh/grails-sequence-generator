/*
 * Copyright (c) 2014 Goran Ehrsson.
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
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.OptimisticLockingFailureException

import java.util.concurrent.ConcurrentHashMap

/**
 * Created by goran on 2014-06-23.
 */
class DefaultSequenceGenerator<T extends Number> implements SequenceGenerator<T> {

    private static final Map<String, SequenceHandle<T>> activeSequences = new ConcurrentHashMap<String, SequenceHandle<T>>()
    private static final Logger log = LoggerFactory.getLogger(DefaultSequenceGenerator.class)

    GrailsApplication grailsApplication

    private boolean keepGoing
    private boolean persisterRunning
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

    private SequenceHandle<T> getHandle(String name, String group = null, Long tenant = null) {
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
                SequenceHandle<T> tmp = activeSequences.get(key)
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
    private SequenceHandle<T> createHandle(final T start, final String format) {
        new SequenceHandle<T>(start, format)
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
            SequenceHandle<T> handle = activeSequences.get(key)
            synchronized (handle) {
                T n = handle.number
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

    @Override
    SequenceStatus createSequence(long tenant, String name, String group, String format, T start) {
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
                            start = config."$name".start ?: (config."$name".initPersister ?: 1L)
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
        new SequenceStatus(name, h.getFormat(), h.getNumber())
    }

    @CompileStatic
    void refresh(long tenant, String name, String group) {
        SequenceNumber n = findNumber(name, group, tenant)
        if (n) {
            SequenceHandle<T> h = getHandle(name, group, tenant)
            if (h) {
                synchronized (h) {
                    if (!h.dirty) {
                        SequenceNumber.withTransaction {
                            n = (SequenceNumber)SequenceNumber.lock(n.id)
                            h.setNumber((T)n.number)
                        }
                    }
                }
            }
        }
    }

    @Override
    @CompileStatic
    String nextNumber(long tenant, String name, String group) {
        getHandle(name, group, tenant).nextFormatted()
    }

    @Override
    @CompileStatic
    T nextNumberLong(long tenant, String name, String group) {
        getHandle(name, group, tenant).next()
    }

    @Override
    @CompileStatic
    SequenceStatus update(long tenant, String name, String group, String format, T current, T start) {
        boolean rval = false
        if(format != null) {
            def definition = findDefinition(name, tenant)
            if(definition != null && definition.format != format) {
                definition.format = format
                definition.save(flush:true)
                rval = true
            }
        }
        if(current != null && start != null) {
            SequenceHandle<T> h = getHandle(name, group, tenant)
            if (h.number == current) {
                synchronized (h) {
                    if (h.getNumber() == current) {
                        h.setNumber(start)
                        rval = true
                    }
                }
            }
        }
        if(rval) {
            def handle = getHandle(name, group, tenant)
            return new SequenceStatus<T>(name, handle.getFormat(), handle.getNumber())
        }
        null
    }

    @Override
    @CompileStatic
    SequenceStatus status(long tenant, String name, String group) {
        SequenceHandle<T> h = getHandle(name, group, tenant)
        new SequenceStatus<T>(name, h.getFormat(), h.getNumber())
    }

    @Override
    Iterable<SequenceStatus> getStatistics(long tenant) {
        SequenceNumber.withTransaction {
            synchronized (activeSequences) {
                flush()
            }
            final List<SequenceNumber> numbers = SequenceNumber.createCriteria().list() {
                definition {
                    eq('tenantId', tenant)
                    order 'name'
                }
                order 'group'
            }
            final List<Map> result = []
            for (SequenceNumber n in numbers) {
                SequenceDefinition d = n.definition
                SequenceHandle<T> handle = getHandle(d.name, n.group, d.tenantId)
                result << new SequenceStatus(d.name, d.format, handle.getNumber())
            }
            result
        }
    }

    void sync() {
        SequenceNumber.withTransaction {
            synchronized (activeSequences) {
                flush()
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

}
