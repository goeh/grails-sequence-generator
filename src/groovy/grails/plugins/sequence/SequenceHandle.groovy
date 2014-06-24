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

import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory storage for sequence counters.
 */
class SequenceHandle<T extends Number> implements grails.plugins.sequence.Sequence<T>, Serializable {
    private final AtomicLong number = new AtomicLong(0L)
    String format
    boolean dirty

    SequenceHandle(T n) {
        this.@number.set(n.longValue())
        dirty = false
    }

    SequenceHandle(T n, String fmt) {
        this.@number.set(n.longValue())
        format = fmt
        dirty = false
    }

    @Override
    public T getNumber() {
        this.@number.get()
    }

    public void setNumber(T n) {
        dirty = true
        this.@number.set(n != null ? n.longValue() : 0L)
    }

    @Override
    public T next() {
        dirty = true
        return this.@number.getAndIncrement();
    }

    @Override
    public String nextFormatted() {
        String.format(format ?: '%s', next())
    }

    @Override
    public String toString() {
        String.format(format ?: '%s', getNumber())
    }
}

