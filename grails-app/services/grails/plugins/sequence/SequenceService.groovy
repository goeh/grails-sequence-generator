package grails.plugins.sequence

import org.springframework.beans.factory.InitializingBean

/**
 * A service that provide sequence counters (for customer numbers, invoice numbers, etc)
 * This service has two primary methods: nextNumber() and nextNumberLong().
 */
class SequenceService implements InitializingBean {

    static transactional = false

    def grailsApplication

    private static final Map map = [:].asSynchronized()

    boolean persisterRunning

    void afterPropertiesSet() {

        loadSequencesFromDatabase()

        def interval = 1000 * (grailsApplication.config.sequence.flushInterval ?: 60)
        persisterRunning = true
        Thread.start {
            while (persisterRunning) {
                Thread.sleep(interval)
                try {
                    log.debug("Scheduled flush")
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
    }

    SequenceHandle initSequence(String name, String group = null, Long tenant = null, Long start = null, String format = null) {
        def key = generateKey(name, group, tenant)
        def h = map.get(key)
        if (!h) {
            synchronized (map) {
                h = map.get(key)
                if (!h) {
                    def config = grailsApplication.config.sequence
                    if (start == null) {
                        start = config."$name".start ?: 1L
                    }
                    if (!format) {
                        format = config."$name".format ?: '%d'
                    }
                    h = createHandle(start, format)
                    map.put(key, h)
                }
            }
        }
        return h
    }

    private String generateKey(String name, String group, Long tenant) {
        final StringBuilder s = new StringBuilder()
        if(name != null) {
            s.append(name)
        }
        s.append('/')
        if(group != null) {
            s.append(group)
        }
        s.append('/')
        if(tenant != null) {
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

    private void loadSequencesFromDatabase() {
        for (n in SequenceNumber.list()) {
            def d = n.definition
            initSequence(d.name, n.group, d.tenantId, n.number, d.format).setNumber(n.number)
        }
        log.debug "Sequences restored from database $map"
    }

    private void terminate() {
        persisterRunning = false
        log.info "sequences terminated $map"
        map.clear()
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
        synchronized (map) {
            flush()
            map.clear()
            loadSequencesFromDatabase()
        }
    }

    private void flush() {
        def dirtyKeys = map.findAll {it.value.dirty}.keySet()

        if (log.isDebugEnabled()) {
            log.debug(dirtyKeys ? "Saving dirty sequences: $dirtyKeys" : "All sequences are clean")
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
                if (log.isDebugEnabled()) {
                    log.debug "Saved sequence $key = $n"
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
