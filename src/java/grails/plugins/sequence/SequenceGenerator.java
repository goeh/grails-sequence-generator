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

package grails.plugins.sequence;

/**
 * Sequence Generator interface.
 */
public interface SequenceGenerator {
    /**
     * Create a new sequence.
     *
     * @param tenant tenant ID
     * @param name   sequence name
     * @param group  sub-sequence
     * @param format number format
     * @param start  start number
     * @return sequence
     */
    grails.plugins.sequence.Sequence createSequence(long tenant, String name, String group, String format, long start);

    /**
     * Get next unique number formatted.
     *
     * @param tenant tenant ID
     * @param name   sequence name
     * @param group  sub-sequence
     * @return formatted number
     */
    String nextNumber(long tenant, String name, String group);

    /**
     * Get next unique (raw) number.
     *
     * @param tenant tenant ID
     * @param name   sequence name
     * @param group  sub-sequence
     * @return number as a long
     */
    Long nextNumberLong(long tenant, String name, String group);

    /**
     * Update sequence.
     *
     * @param tenant  tenant ID
     * @param name    sequence name
     * @param group   sub-sequence
     * @param format  number format
     * @param current current number
     * @param start   new number
     * @return sequence status if sequence was updated, null otherwise
     */
    SequenceStatus update(long tenant, String name, String group, String format, Long current, Long start);

    /**
     * Get sequence statistics.
     *
     * @param tenant tenant ID
     * @return statistics for all sequences in the tenant
     */
    Iterable<SequenceStatus> getStatistics(long tenant);
}
