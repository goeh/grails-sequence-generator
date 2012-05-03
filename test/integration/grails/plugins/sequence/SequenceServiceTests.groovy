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

    void testNoFormat() {
        def name = "Company"
        sequenceGeneratorService.initSequence(name, null, null, 1)
        assertEquals 1L, sequenceGeneratorService.nextNumberLong(name)
        assertEquals "2", sequenceGeneratorService.nextNumber(name)
        assertEquals 3L, sequenceGeneratorService.nextNumberLong(name)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals "4", sequenceGeneratorService.nextNumber('Company')

        sequenceGeneratorService.reset()
    }

    void testFormat() {
        def seq = sequenceGeneratorService.initSequence('Customer', null, null, 100, 'K-%04d')

        assertEquals 'K-0100', sequenceGeneratorService.nextNumber('Customer')
        assertEquals 'K-0101', sequenceGeneratorService.nextNumber('Customer')
        assertEquals 'K-0102', sequenceGeneratorService.nextNumber('Customer')

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals 'K-0103', sequenceGeneratorService.nextNumber('Customer')

        //sequenceGeneratorService.save()

        assertEquals 104, seq.number

        sequenceGeneratorService.reset()
    }

    void testThreads() {
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
        assertEquals 1000 + THREADS * NUMBERS, sequenceGeneratorService.nextNumberLong(sequenceName)

        Thread.sleep(3000) // Wait for persister to finish

        assertEquals 1001 + THREADS * NUMBERS, sequenceGeneratorService.nextNumberLong(sequenceName)

        sequenceGeneratorService.reset()
    }

    def testDomainMethod() {
        sequenceGeneratorService.initSequence(SequenceTestEntity.simpleName, null, null, 1000, '%05d')
        assertEquals "01000", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01001", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01002", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01003", new SequenceTestEntity().getNextSequenceNumber()

        sequenceGeneratorService.reset()
    }

    def testStartWithZero() {
        sequenceGeneratorService.initSequence("Zero", null, null, 0)
        assertEquals "0", sequenceGeneratorService.nextNumber("Zero")

        sequenceGeneratorService.reset()
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

        sequenceGeneratorService.reset()
    }

    def testTerminate() {
        sequenceGeneratorService.initSequence(SequenceTestEntity.simpleName, null, null, 1000, '%05d')
        assertEquals "01000", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01001", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01002", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01003", new SequenceTestEntity().getNextSequenceNumber()

        def t1 = System.currentTimeMillis()
        sequenceGeneratorService.terminate()
        def t2 = System.currentTimeMillis()

        assertFalse sequenceGeneratorService.persisterRunning
        assertTrue ((t2 - t1) < 1000)
    }
}
