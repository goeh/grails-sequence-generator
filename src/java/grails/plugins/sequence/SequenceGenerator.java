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
    grails.plugins.sequence.Sequence createSequence(long tenant, String name, String group, String format, long start);

    String nextNumber(long tenant, String name, String group);

    Long nextNumberLong(long tenant, String name, String group);

    SequenceStatus update(long tenant, String name, String group, String format, Long current, Long start);

    Iterable<SequenceStatus> getStatistics(long tenant);

    void refresh(long tenant, String name, String group);

    void sync();

    void shutdown();
}
