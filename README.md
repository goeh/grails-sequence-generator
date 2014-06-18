# Grails Sequence Generator Plugin

The sequence generator plugin provides a simple way to add sequence counters
to Grails applications. You can control the starting number, the format and
you can have different sequence counters based on application logic.

**Example**

    sequenceService.initSequence('WebOrder', null, null, 100, 'WEB-%04d')

    assert sequenceGeneratorService.nextNumber('WebOrder') == 'WEB-0100'
    assert sequenceGeneratorService.nextNumber('WebOrder') == 'WEB-0101'
    assert sequenceGeneratorService.nextNumber('WebOrder') == 'WEB-0102'

    assert sequenceGeneratorService.nextNumberLong('WebOrder') == 103
    assert sequenceGeneratorService.nextNumberLong('WebOrder') == 104

The SequenceGeneratorService implementation is very efficient and can provide
sequential numbers to concurrent threads without problems.

Sequences are persisted to database to survive server restarts.

Because it's common to have sequence properties on domain classes (customer number, order number, etc)
there is an annotation that does all the plumbing for you.
Domain classes annotated with *grails.plugins.sequence.SequenceEntity*
will get a *number* property added at compile that will be initialized with
a unique sequence number when the domain instance is saved to database.

## Configuration

**sequence.flushInterval** (default 60)

Number of seconds to wait before flushing in-memory sequence counters to disk.

    sequence.flushInterval = 300
    
**sequence.(name).format** (default %d)

Format to use for sequence numbers. The name is the name of the sequence (simple name of the domain class).
The number is formatted with *String#format(String, Object...)*.

    sequence.Customer.format = "%05d"

**sequence.(name).start** (default 1)

The starting number when a sequence is first initialized. The name is the name of the sequence (simple name of the domain class).

    sequence.Customer.start = 1001

## @SequenceEntity

If you have a sequence property on a domain class, for example a customer number property, you could add code
in beforeValidate() or beforeInsert() that assigns a sequence number with *sequenceGeneratorService.nextNumber(this.class)*.
But the *grails.plugins.sequence.SequenceEntity* annotation makes this much easier. It does all the plumbing for you.

    @SequenceEntity
    class Customer {
        ...
    }
    
An AST Transformation adds a *String* property called **number** to the domain class at compile time.
The property will have *maxSize:10*, *unique:true*, and *blank:false* constraints. But you can override this in the annotation.
 
    @SequenceEntity(property = "orderNumber", maxSize = 20, blank = false, unique = true) 
    class CustomerOrder {
        ...
    }

The AST Transformation will also add code in *beforeValidate()* that sets the *number* property if it is not already set.

So the only thing you really have to do is to annotate your domain class with *@SequenceEntity* and the number
property will be set to a new unique number before the domain instance is saved to the database.
 
**Maybe you ask: "Why not use database sequences?"**

Well, a database sequence use numbers only and is very efficient but not so flexible.
This plugin is more flexible and lets you use String properties and prefix/suffix the number with characters.
You can use sub-sequences to generate different numbers depending on application logic.
Maybe domain instances of one category should use another sequence that the default.
This plugin also let you change the sequence number programatically.
For example you could reset the sequence to start with YYYY0001 on the first of January every year.
 
## SequenceGeneratorService

With *SequenceGeneratorService* you can interact with sequences. The following methods are available:

**def initSequence(String name, String group = null, Long tenant = null, Long start = null, String format = null)**

Create a new sequence counter and initialize it with a starting number (default 1).

Parameter   | Description
----------- | ---------------------
name        | Name of sequence
group       | If you need multiple sequences for the same domain class based on some application logic you can use groups to create sub-sequences
tenant      | Tenant ID in a multi-tenant environment
start       | The sequence will start at this number
format      | The number format returned by *nextNumber()* uses String#format(String, Object...)

**def initSequence(Class clazz, String group = null, Long tenant = null, Long start = null, String format = null)**

Same as above but takes a domain class instead of sequence name. Class#getSimpleName() will be used as sequence name.

**String nextNumber(String name, String group = null, Long tenant = null)**

Returns the next number in the specified sequence. The number is formatted with the sequence's defined format.

Parameter   | Description
----------- | ---------------------
name        | Name of sequence
group       | Optional sub-sequence if multiple sequence counters exists for the same name / domain class
tenant      | Tenant ID in a multi-tenant environment

**String nextNumber(Class clazz, String group = null, Long tenant = null)**

Same as above but takes a domain class instead of sequence name. Class#getSimpleName() will be used as sequence name.

**boolean setNextNumber(Long currentNumber, Long newNumber, String name, String group = null, Long tenant = null)**

Sets the next number for a sequence counter.
To avoid concurrency issues you must specify both the current number and the number you want to change to.
If current number is not equal to the specified current number the new number will not be set.
True is returned if the sequence number was updated.

Parameter     | Description
------------- | ---------------------
currentNumber | The caller's view of what the current number is
newNumber     | The number to set. The next call to *nextNumber()* will get this number
name          | Name of sequence to set number for
group         | Optional sub-sequence if multiple sequence counters exists for the same name / domain class
tenant        | Tenant ID in a multi-tenant environment

**Long refresh(Class clazz, String group = null, Long tenant = null)**

Sequences are kept in memory for performance reasons and periodically written to the database.
Calling refresh() will discard any updates made in memory and re-initialize the sequence from database.

**void shutdown()**

Flush all sequences and terminate the service.

**List<Map> statistics(Long tenant = null)**

Return statistics for all sequences defined in the application.
Statistics are returned as a List of Maps with the following keys:

Key    | Value
------ | -----------------
name   | Name of the sequence
format | The sequence number format
number | Next number that will be returned for this sequence

## REST Service

*SequenceGeneratorController* provides two methods that accepts JSON requests to interact with sequences.
**Make sure you protect this controller with appropriate access control**.

**list(String name, String group)**

Returns a list of sequences in JSON format. See **SequenceGeneratorService#getStatistics()**

**update(String name, String group, Long current, Long next)**

Update the next number for a sequence. See *SequenceGeneratorService#setNextNumber()*

## JMX

You can check sequence statistics from a JMX client using the registered JMX bean *:name=SequenceGeneratorService,type=services*. 

## Road Map

### Admin UI
Provide a user interface for managing sequence definitions.
Administrators must be able to change number format and next available number.


## Miscellaneous

- The [GR8 CRM ecosystem](http://gr8crm.github.io) uses sequence-generator plugin to generate customer, order and invoice numbers.
