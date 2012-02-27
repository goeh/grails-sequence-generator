#Grails Sequence Plugin

The sequence plugin provides a simple way to add sequence counters
to Grails applications.

    sequenceService.initSequence('WebOrder', null, null, 100, 'WEB-%04d')

    assert sequenceService.nextNumber('WebOrder') == 'WEB-0100'
    assert sequenceService.nextNumber('WebOrder') == 'WEB-0101'
    assert sequenceService.nextNumber('WebOrder') == 'WEB-0102'

    assert sequenceService.nextNumberLong('WebOrder') == 103
    assert sequenceService.nextNumberLong('WebOrder') == 104

The SequenceService implementation is very efficient and can provide
sequential numbers to concurrent threads without problems.

Sequences are persisted to database to survive server restarts.
