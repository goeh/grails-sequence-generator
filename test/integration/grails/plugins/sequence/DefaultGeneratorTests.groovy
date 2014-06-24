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

/**
 * Created by goran on 2014-06-24.
 */
class DefaultGeneratorTests extends GroovyTestCase {

    def sequenceGeneratorService
    def sequenceGenerator

    void tearDown() {
        super.tearDown()
        sequenceGenerator.shutdown()
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

        sequenceGenerator.shutdown()

        assertEquals '131004', sequenceGeneratorService.nextNumber(name)
        assertEquals '131005', sequenceGeneratorService.nextNumber(name)
        assertEquals '131006', sequenceGeneratorService.nextNumber(name)
    }

    def testThreads() {
        def sequenceName = 'ThreadTestDefault'

        final int THREADS = 100
        final int NUMBERS = 1000

        sequenceGeneratorService.initSequence(sequenceName, null, null, 1000)

        // Start THREADS threads that grab NUMBERS numbers each.
        def slots = new ArrayList(THREADS)
        def threads = new ArrayList(THREADS)
        for (int i = 0; i < THREADS; i++) {
            def arr = slots[i] = new ArrayList(NUMBERS + 1)
            def runnable = {
                arr << System.currentTimeMillis()
                SequenceDefinition.withNewSession {
                    //println "Thread ${Thread.currentThread().id} started"
                    NUMBERS.times {
                        arr << sequenceGeneratorService.nextNumberLong(sequenceName)
                        Thread.currentThread().sleep(10)
                    }
                    arr[0] = System.currentTimeMillis() - arr[0]
                    if ((Thread.currentThread().id.intValue() % (THREADS / 3).intValue()) == 0) {
                        sequenceGenerator.refresh(0L, sequenceName, null)
                        // Be evil and reset all counters from db in the middle of the test.
                    }
                    //println "Thread ${Thread.currentThread().id} finished"
                }
            }
            def t = new Thread(runnable, sequenceName + i)
            t.priority = Thread.MIN_PRIORITY
            threads << t
            t.start()
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

    def testShutdown() {
        sequenceGeneratorService.initSequence(SequenceTestEntity, null, null, 1008, '%05d')
        assertEquals "01008", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01009", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01010", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "01011", new SequenceTestEntity().getNextSequenceNumber()

        sequenceGenerator.shutdown()

        assertFalse sequenceGenerator.keepGoing

        assertEquals "01012", new SequenceTestEntity().getNextSequenceNumber()
        assertTrue sequenceGenerator.persisterRunning
        assertTrue sequenceGenerator.keepGoing
    }

    def testRefresh() {
        sequenceGeneratorService.initSequence(SequenceTestEntity, null, null, 5001, '%04d')
        assertEquals "5001", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "5002", new SequenceTestEntity().getNextSequenceNumber()
        assertEquals "5003", new SequenceTestEntity().getNextSequenceNumber()

        sequenceGenerator.sync()

        def n = SequenceNumber.createCriteria().get {
            definition {
                eq('name', SequenceTestEntity.class.simpleName)
                eq('tenantId', 0L)
            }
            isNull('group')
        }
        assertNotNull n

        n.number = 2001
        n.save(flush: true)

        sequenceGenerator.refresh(0L, SequenceTestEntity.simpleName, null)

        assertEquals "2001", new SequenceTestEntity().getNextSequenceNumber()
    }
}
