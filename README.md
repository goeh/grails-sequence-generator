#Grails Sequence Generator Plugin

The sequence generator plugin provides a simple way to add sequence counters
to Grails applications.

    sequenceService.initSequence('WebOrder', null, null, 100, 'WEB-%04d')

    assert sequenceGeneratorService.nextNumber('WebOrder') == 'WEB-0100'
    assert sequenceGeneratorService.nextNumber('WebOrder') == 'WEB-0101'
    assert sequenceGeneratorService.nextNumber('WebOrder') == 'WEB-0102'

    assert sequenceGeneratorService.nextNumberLong('WebOrder') == 103
    assert sequenceGeneratorService.nextNumberLong('WebOrder') == 104

The SequenceGeneratorService implementation is very efficient and can provide
sequential numbers to concurrent threads without problems.

Sequences are persisted to database to survive server restarts.
