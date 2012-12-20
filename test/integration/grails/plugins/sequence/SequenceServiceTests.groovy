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

class SequenceServiceTests extends GroovyTestCase {

    def sequenceGeneratorService

    void tearDown() {
        super.tearDown()
        sequenceGeneratorService.reset()
    }

    def testNoFormat() {
        def name = "Company"
        sequenceGeneratorService.initSequence(name, null, null, 1)
        assertEquals 1L, sequenceGeneratorService.nextNumberLong(name)
        assertEquals "2", sequenceGeneratorService.nextNumber(name)
        assertEquals 3L, sequenceGeneratorService.nextNumberLong(name)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals "4", sequenceGeneratorService.nextNumber(name)
    }

    def testFormat() {
        def name = "Customer"
        def seq = sequenceGeneratorService.initSequence(name, null, null, 100, 'K-%04d')

        assertEquals 'K-0100', sequenceGeneratorService.nextNumber(name)
        assertEquals 'K-0101', sequenceGeneratorService.nextNumber(name)
        assertEquals 'K-0102', sequenceGeneratorService.nextNumber(name)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals 'K-0103', sequenceGeneratorService.nextNumber(name)

        //sequenceGeneratorService.save()

        assertEquals 104, seq.number
    }

    def testSetNumber() {
        def name = "Ticket"
        def seq = sequenceGeneratorService.initSequence(name, null, null, 121001)

        assertEquals '121001', sequenceGeneratorService.nextNumber(name)
        assertEquals '121002', sequenceGeneratorService.nextNumber(name)
        assertEquals '121003', sequenceGeneratorService.nextNumber(name)

        assert !sequenceGeneratorService.setNextNumber(121003, 131001, name)
        assert sequenceGeneratorService.setNextNumber(121004, 131001, name)

        assertEquals '131001', sequenceGeneratorService.nextNumber(name)
        assertEquals '131002', sequenceGeneratorService.nextNumber(name)
        assertEquals '131003', sequenceGeneratorService.nextNumber(name)

        sequenceGeneratorService.reset()

        assertEquals '131004', sequenceGeneratorService.nextNumber(name)
        assertEquals '131005', sequenceGeneratorService.nextNumber(name)
        assertEquals '131006', sequenceGeneratorService.nextNumber(name)
    }

    def testThreads() {
        def sequenceName = 'ThreadTest'

        final int THREADS = 100
        final int NUMBERS = 1000

        sequenceGeneratorService.initSequence(sequenceName, null, null, 1000)

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
                        arr << sequenceGeneratorService.nextNumberLong(sequenceName)
                    }
                    arr[0] = System.currentTimeMillis() - arr[0]
                    if ((Thread.currentThread().id.intValue() % (THREADS / 3).intValue()) == 0) {
                        sequenceGeneratorService.reset() // Be evil and reset all counters from db in the middle of the test.
                    }
                    //println "Thread ${Thread.currentThread().id} finished"
                }
                Thread.sleep(50 + new Random().nextInt(50))
            }
        }
        threads.each { it.join() }  // Wait for all threads to finish.

        long time = 0L
        slots.eachWithIndex { arr, i ->
            def end = arr.size() - 1
            for (int n = 1; n < end; n++) {
                int nbr = arr[n]
                slots.eachWithIndex { other, l ->
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
        assertEquals 1000 + THREADS * NUMBERS, sequenceGeneratorService.nextNumberLong(sequenceName)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals 1001 + THREADS * NUMBERS, sequenceGeneratorService.nextNumberLong(sequenceName)
    }

    def testDomainMethod() {
        sequenceGeneratorService.initSequence(SequenceTestEntity, null, null, 1000, '%05d')
        assertEquals "01000", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01001", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01002", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01003", new SequenceTestEntity().getNextSequenceNumber()
    }

    def testBeforeValidate() {
        //sequenceGeneratorService.initSequence(SequenceTestEntity, null, null, 1000)
        def test = new SequenceTestEntity(name: "TEST")
        assert test.respondsTo("beforeValidate")
        assert test.number == null
        test.beforeValidate()
        assert test.number == "01004"
    }

    def testStatistics() {
        sequenceGeneratorService.initSequence('Foo', null, null, 25)
        sequenceGeneratorService.initSequence('Bar', null, null, 50, 'B%d')

        5.times {
            sequenceGeneratorService.nextNumber('Foo')
            sequenceGeneratorService.nextNumber('Bar')
        }

        sequenceGeneratorService.flush()

        def stats = sequenceGeneratorService.statistics()
        println "stats=$stats"
        def foo = stats.find { it.name == 'Foo' }
        assert foo != null
        assert foo.format == '%d'
        assert foo.number == 30L
        def bar = stats.find { it.name == 'Bar' }
        assert bar != null
        assert bar.name == 'Bar'
        assert bar.format == 'B%d'
        assert bar.number == 55L
    }

    def testClassArgument() {
        //sequenceGeneratorService.initSequence(SequenceTestEntity, null, null, 1000, '%05d')
        assertEquals "01005", sequenceGeneratorService.nextNumber(SequenceTestEntity)
        assertEquals "01006", sequenceGeneratorService.nextNumber(SequenceTestEntity)
        assertEquals "01007", sequenceGeneratorService.nextNumber(SequenceTestEntity)
    }

    def testStartWithZero() {
        sequenceGeneratorService.initSequence("Zero", null, null, 0)
        assertEquals "0", sequenceGeneratorService.nextNumber("Zero")
    }

    def testMultiTenant() {
        sequenceGeneratorService.initSequence("TenantTest", null, 0, 100)
        sequenceGeneratorService.initSequence("TenantTest", null, 1, 100)
        sequenceGeneratorService.initSequence("TenantTest", null, 2, 200)
        assertEquals 100, sequenceGeneratorService.nextNumberLong("TenantTest", null, 0)
        assertEquals 100, sequenceGeneratorService.nextNumberLong("TenantTest", null, 1)
        assertEquals 200, sequenceGeneratorService.nextNumberLong("TenantTest", null, 2)
        assertEquals 101, sequenceGeneratorService.nextNumberLong("TenantTest", null, 0)
        assertEquals 102, sequenceGeneratorService.nextNumberLong("TenantTest", null, 0)
        assertEquals 103, sequenceGeneratorService.nextNumberLong("TenantTest", null, 0)
        assertEquals 201, sequenceGeneratorService.nextNumberLong("TenantTest", null, 2)
        assertEquals 202, sequenceGeneratorService.nextNumberLong("TenantTest", null, 2)
        assertEquals 203, sequenceGeneratorService.nextNumberLong("TenantTest", null, 2)
        assertEquals 101, sequenceGeneratorService.nextNumberLong("TenantTest", null, 1)
        assertEquals 102, sequenceGeneratorService.nextNumberLong("TenantTest", null, 1)
    }

    def testTerminate() {
        //sequenceGeneratorService.initSequence(SequenceTestEntity, null, null, 1000, '%05d')
        assertEquals "01008", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01009", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01010", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01011", new SequenceTestEntity().getNextSequenceNumber()

        def t1 = System.currentTimeMillis()
        sequenceGeneratorService.terminate()
        def t2 = System.currentTimeMillis()

        assertFalse sequenceGeneratorService.persisterRunning
        assertTrue((t2 - t1) < 1000)
    }

}
