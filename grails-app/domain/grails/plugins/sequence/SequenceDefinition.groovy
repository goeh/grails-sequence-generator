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
 * Domain class for persisting sequence definitions.
 */
class SequenceDefinition {

    Long tenantId
    String name
    String format
    static hasMany = [numbers:SequenceNumber]
    static constraints = {
        tenantId(nullable:true)
        name(unique:'tenantId', blank:false, maxSize:100)
        format(nullable:true, maxSize:100)
    }
    static mapping = {
        cache true
    }

    @Override
    String toString() {
        tenantId != null ? "${tenantId}.${name}".toString() : name
    }

    @Override
    int hashCode() {
        int hash = 17
        if(id != null) hash = hash * 17 + id * 17
        if(version != null) hash = hash * 17 + version * 17
        if(tenantId != null) hash = hash * 17 + tenantId * 17
        if(name != null) hash = hash * 17 + name.hashCode()
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
        if (!(other.instanceOf(SequenceDefinition))) {
            return false
        }
        if(!(this.id != null ? this.id.equals(other.id) : other.id == null)) {
            return false
        }
        if(!(this.version != null ? this.version.equals(other.version) : other.version == null)) {
            return false
        }
        if(!(this.tenantId != null ? this.tenantId.equals(other.tenantId) : other.tenantId == null)) {
            return false
        }
        if(!(this.name != null ? this.name.equals(other.name) : other.name == null)) {
            return false
        }
        return true
    }
}
