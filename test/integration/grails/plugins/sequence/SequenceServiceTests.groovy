package grails.plugins.sequence

class SequenceServiceTests extends GroovyTestCase {

    def sequenceService

    void testNoFormat() {
        def name = "Company"
        sequenceService.initSequence(name, null, null, 1)
        assertEquals 1L, sequenceService.nextNumberLong(name)
        assertEquals "2", sequenceService.nextNumber(name)
        assertEquals 3L, sequenceService.nextNumberLong(name)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals "4", sequenceService.nextNumber('Company')

        sequenceService.reset()
    }

    void testFormat() {
        def seq = sequenceService.initSequence('Customer', null, null, 100, 'K-%04d')

        assertEquals 'K-0100', sequenceService.nextNumber('Customer')
        assertEquals 'K-0101', sequenceService.nextNumber('Customer')
        assertEquals 'K-0102', sequenceService.nextNumber('Customer')

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals 'K-0103', sequenceService.nextNumber('Customer')

        //sequenceService.save()

        assertEquals 104, seq.number

        sequenceService.reset()
    }

    void testThreads() {
        def sequenceName = 'ThreadTest'

        final int THREADS = 100
        final int NUMBERS = 1000

        sequenceService.initSequence(sequenceName, null, null, 1000)

        // Start THREADS threads that grab NUMBERS numbers each.
        def slots = new ArrayList(THREADS)
        def threads = new ArrayList(THREADS)
        for (int i = 0; i < THREADS; i++) {
            def arr = slots[i] = new ArrayList(NUMBERS + 1)
            threads << Thread.start {
                arr << System.currentTimeMillis()
                SequenceDefinition.withNewSession {
                    //println "Thread ${Thread.currentThread().id} starting..."
                    NUMBERS.times {
                        arr << sequenceService.nextNumberLong(sequenceName)
                    }
                    arr[0] = System.currentTimeMillis() - arr[0]
                    if ((Thread.currentThread().id.intValue() % (THREADS / 3).intValue()) == 0) {
                        sequenceService.reset() // Be evil and reset all counters from db in the middle of the test.
                    }
                    //println "Thread ${Thread.currentThread().id} finished"
                }
                Thread.sleep(50 + new Random().nextInt(50))
            }
        }
        threads.each {it.join()}  // Wait for all threads to finish.

        long time = 0L
        slots.eachWithIndex {arr, i ->
            def end = arr.size() - 1
            for (int n = 1; n < end; n++) {
                int nbr = arr[n]
                slots.eachWithIndex {other, l ->
                    if ((l != i) && other.contains(nbr)) {
                        println "slot[$i] = ${slots[i][1..-1].join(',')}"
                        println "slot[$l] = ${slots[l][1..-1].join(',')}"
                        fail "slot[$l] and slot[$i] both contains $nbr"
                    }
                }
            }
            time += arr[0]
            println "${String.format('%3d', i + 1)} ${slots[i][1..15].join(',')}... (${slots[i][0]} ms)"
        }

        println "Average time ${(time / slots.size()).intValue()} ms"
        assertEquals 1000 + THREADS * NUMBERS, sequenceService.nextNumberLong(sequenceName)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals 1001 + THREADS * NUMBERS, sequenceService.nextNumberLong(sequenceName)

        sequenceService.reset()
    }

    def testDomainMethod() {
        sequenceService.initSequence(SequenceTestEntity.simpleName, null, null, 1000, '%05d')
        assertEquals "01000", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01001", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01002", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01003", new SequenceTestEntity().getNextSequenceNumber()

        sequenceService.reset()
    }

    def testStartWithZero() {
        sequenceService.initSequence("Zero", null, null, 0)
        assertEquals "0", sequenceService.nextNumber("Zero")

        sequenceService.reset()
    }

    def testMultiTenant() {
        sequenceService.initSequence("TenantTest", null, 0, 100)
        sequenceService.initSequence("TenantTest", null, 1, 100)
        sequenceService.initSequence("TenantTest", null, 2, 200)
        assertEquals 100, sequenceService.nextNumberLong("TenantTest", null, 0)
        assertEquals 100, sequenceService.nextNumberLong("TenantTest", null, 1)
        assertEquals 200, sequenceService.nextNumberLong("TenantTest", null, 2)
        assertEquals 101, sequenceService.nextNumberLong("TenantTest", null, 0)
        assertEquals 102, sequenceService.nextNumberLong("TenantTest", null, 0)
        assertEquals 103, sequenceService.nextNumberLong("TenantTest", null, 0)
        assertEquals 201, sequenceService.nextNumberLong("TenantTest", null, 2)
        assertEquals 202, sequenceService.nextNumberLong("TenantTest", null, 2)
        assertEquals 203, sequenceService.nextNumberLong("TenantTest", null, 2)
        assertEquals 101, sequenceService.nextNumberLong("TenantTest", null, 1)
        assertEquals 102, sequenceService.nextNumberLong("TenantTest", null, 1)

        sequenceService.reset()
    }
}
