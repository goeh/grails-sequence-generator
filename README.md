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

Domain classes annotated with grails.plugins.sequence.SequenceEntity
will get a 'number' property added at compile time and a
getNextSequenceNumber() method at runtime.

## Road Map

### Admin UI
Provide a user interface for managing sequence definitions.
Administrators must be able to change number format and next available number.

### Optimization
When a sequence number is accessed first time after JVM boot *all* sequences are initialized by
SequenceGeneratorService#loadSequencesFromDatabase(). This becomes a problem in an application
with lots of sequences (multi-tenant environments).
Change the init-implementation to lazy load only the requested sequence from database.