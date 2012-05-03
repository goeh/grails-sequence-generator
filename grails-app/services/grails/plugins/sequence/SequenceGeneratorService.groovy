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

/**
 * A service that provide sequence counters (for customer numbers, invoice numbers, etc)
 * This service has two primary methods: nextNumber() and nextNumberLong().
 */
class SequenceGeneratorService {

    static transactional = false

    def grailsApplication

    private static final Map myMap = [:].asSynchronized()

    boolean persisterRunning
    boolean initialized

    private Thread persisterThread

    private Map getMap() {
        if (!initialized) {
            synchronized (myMap) {
                if (!initialized) {
                    loadSequencesFromDatabase(myMap)

                    def interval = 1000 * (grailsApplication.config.sequence.flushInterval ?: 60)
                    persisterRunning = true
                    persisterThread = new Thread("GrailsSequenceGenerator")
                    persisterThread.start {
                        while (persisterRunning) {
                            Thread.sleep(interval)
                            try {
                                log.trace("Scheduled flush")
                                flush()
                            } catch(InterruptedException e) {
                                log.info("Sequence flusher thread interrupted")
                                flush()
                            } catch (Exception e) {
                                if (log != null) {
                                    log.error "Failed to flush sequences to database!", e
                                } else {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }

                    Runtime.runtime.addShutdownHook {
                        terminate()
                    }

                    initialized = true
                }
            }
        }
        return myMap
    }

    SequenceHandle initSequence(String name, String group = null, Long tenant = null, Long start = null, String format = null) {
        doInitSequence(map, name, group, tenant, start, format)
    }

    private SequenceHandle doInitSequence(Map m, String name, String group = null, Long tenant = null, Long start = null, String format = null) {
        def key = generateKey(name, group, tenant)
        def h = m.get(key)
        if (!h) {
            synchronized (m) {
                h = m.get(key)
                if (!h) {
                    def config = grailsApplication.config.sequence
                    if (start == null) {
                        start = config."$name".start ?: 1L
                    }
                    if (!format) {
                        format = config."$name".format ?: '%d'
                    }
                    h = createHandle(start, format)
                    m.put(key, h)
                    log.debug "Created new sequence $key with format [$format]"
                }
            }
        }
        return h
    }

    private String generateKey(String name, String group, Long tenant) {
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

    private SequenceHandle createHandle(Long start, String format) {
        new SequenceHandle(start, format)
    }

    String nextNumber(String name, String group = null, Long tenant = null) {
        initSequence(name, group, tenant).nextFormatted()
    }

    Long nextNumberLong(String name, String group = null, Long tenant = null) {
        initSequence(name, group, tenant).next()
    }

    private void loadSequencesFromDatabase(Map m) {
        for (n in SequenceNumber.list()) {
            def d = n.definition
            doInitSequence(m, d.name, n.group, d.tenantId, n.number, d.format).setNumber(n.number)
        }
        log.debug "Sequences restored from database $m"
    }

    private void terminate() {
        persisterRunning = false
        persisterThread.interrupt()
        persisterThread = null
        log.info "sequences terminated $map"
        myMap.clear()
        initialized = false
    }

    def shutdown() {
        try {
            flush()
        } catch (Exception e) {
            log.error "Failed to save sequence counters!", e
        }
        terminate()
    }

    void reset() {
        log.debug "Resetting sequence counters..."
        synchronized (myMap) {
            flush()
            myMap.clear()
            loadSequencesFromDatabase(myMap)
        }
    }

    private void flush() {
        def dirtyKeys = map.findAll {it.value.dirty}.keySet()

        if (dirtyKeys) {
            if (log.isDebugEnabled()) {
                log.debug("Saving dirty sequences: $dirtyKeys")
            }
        } else if (log.isTraceEnabled()) {
            log.trace("All sequences are clean")
        }

        for (key in dirtyKeys) {
            def parts = key.split('/').toList()
            def name = parts[0]
            def group = parts[1] ?: null
            def tenant = parts[2] ? Long.valueOf(parts[2]) : null
            SequenceHandle handle = map.get(key)
            synchronized (handle) {
                def n = handle.number
                SequenceDefinition.withTransaction {tx ->
                    def seq = findNumber(name, group, tenant)
                    if (seq) {
                        seq = SequenceNumber.lock(seq.id)
                        seq.number = n
                        seq.save(failOnError: true)
                    } else {
                        def d = findDefinition(name, tenant)
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

    private SequenceDefinition findDefinition(String name, Long tenant) {
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

    private SequenceNumber findNumber(String name, String group, Long tenant) {
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

}
