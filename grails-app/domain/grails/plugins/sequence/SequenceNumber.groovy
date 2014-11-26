/*
 * Copyright (c) 2012 Goran Ehrsson.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  under the License.
 */
package grails.plugins.sequence

/**
 * Domain class that holds the next available sequence number for a specific sequence definition.
 */
class SequenceNumber {

    String group
    Long number

    static belongsTo = [definition:SequenceDefinition]
    static constraints = {
        group(nullable:true, blank:false, maxSize:40, unique:'definition')
    }
    static mapping = {
        group column:'sequence_group'
        number column:'sequence_number'
    }

    SequenceHandle toHandle() {
        new SequenceHandle(number, definition.format)
    }

    @Override
    String toString() {
        "$number"
    }

    @Override
    int hashCode() {
        int hash = 17
        if(id != null) hash = hash * 17 + id * 17
        if(version != null) hash = hash * 17 + version * 17
        if(group != null) hash = hash * 17 + group.hashCode()
        if(number != null) hash = hash * 17 + number.hashCode()
        return hash
    }

    @Override
    boolean equals(other) {
        if(this.is(other)) {
            return true
        }
        if(other == null) {
            return false
        }
        if (!(other.instanceOf(SequenceNumber))) {
            return false
        }
        if(!(this.id != null ? this.id.equals(other.id) : other.id == null)) {
            return false
        }
        if(!(this.version != null ? this.version.equals(other.version) : other.version == null)) {
            return false
        }
        if(!(this.group != null ? this.group.equals(other.group) : other.group == null)) {
            return false
        }
        if(!(this.number != null ? this.number.equals(other.number) : other.number == null)) {
            return false
        }
        return true
    }
}
