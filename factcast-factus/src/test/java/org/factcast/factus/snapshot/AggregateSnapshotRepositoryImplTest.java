/*
 * Copyright © 2017-2020 factcast.org
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
 */
package org.factcast.factus.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.core.snap.SnapshotId;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.AggregateUtil;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AggregateSnapshotRepositoryImplTest {

    @Mock
    private SnapshotCache snap;

    @Mock
    private SnapshotSerializerSupplier snapshotSerializerSupplier;

    @InjectMocks
    private AggregateSnapshotRepositoryImpl underTest;

    @Nested
    class WhenFindingLatest {

        private UUID aggregateId = UUID.randomUUID();

        @Test
        void findNone() {
            Optional<Snapshot> result = underTest.findLatest(WithSVUID.class, aggregateId);

            assertThat(result)
                    .isEmpty();
        }

        @Test
        void findOne() {
            // INIT
            Snapshot withSVUID = new Snapshot(new SnapshotId("some key", aggregateId), UUID
                    .randomUUID(),
                    new byte[0], false);

            when(snap.getSnapshot(any()))
                    .thenReturn(Optional.of(withSVUID));

            // RUN
            Optional<Snapshot> result = underTest.findLatest(WithSVUID.class, aggregateId);

            // ASSERT
            assertThat(result)
                    .isPresent()
                    .get()
                    .extracting("id.uuid")
                    .isEqualTo(aggregateId);
        }
    }

    @Nested
    class WhenGettingSerialVersionUid {

        @Test
        void retrievesExistingSVUID() {
            assertEquals(42, underTest.getSerialVersionUid(WithSVUID.class));
            assertEquals(0, underTest.getSerialVersionUid(WithoutSVUID.class));
        }
    }

    @Nested
    class WhenCreatingKey {

        @Test
        void createsKeyIncludingSerialVersionUid() {
            String with = underTest.createKeyForType(WithSVUID.class);
            String without = underTest.createKeyForType(WithoutSVUID.class);

            assertThat(with).contains(WithSVUID.class.getCanonicalName()).contains(":42");
            assertThat(without).contains(WithoutSVUID.class.getCanonicalName()).contains(":0");

        }
    }

    @Nested
    class WhenPutting {
        private final UUID STATE = UUID.randomUUID();

        private WithoutSVUID aggregate = new WithoutSVUID();

        @Mock
        private SnapshotSerializer snapshotSerializer;

        @Captor
        private ArgumentCaptor<Snapshot> snapshotCaptor;

        @Test
        void put() {
            // INIT
            when(snapshotSerializerSupplier.retrieveSerializer(any()))
                    .thenReturn(snapshotSerializer);

            when(snapshotSerializer.serialize(aggregate))
                    .thenReturn("foo".getBytes());
            when(snapshotSerializer.includesCompression())
                    .thenReturn(true);

            AggregateUtil.aggregateId(aggregate, UUID.randomUUID());

            // RUN
            CompletableFuture<Void> result = underTest.put(aggregate, STATE);

            // ASSERT
            assertThat(result)
                    .succeedsWithin(Duration.ofSeconds(5));

            verify(snapshotSerializerSupplier)
                    .retrieveSerializer(any());

            verify(snap)
                    .setSnapshot(snapshotCaptor.capture());

            assertThat(snapshotCaptor.getValue())
                    .isNotNull()
                    .extracting(Snapshot::lastFact,
                            Snapshot::bytes,
                            Snapshot::compressed)
                    .containsExactly(STATE, "foo".getBytes(), true);
        }
    }

    public static class WithSVUID extends Aggregate {
        private static final long serialVersionUID = 42L;
    }

    public static class WithoutSVUID extends Aggregate {
    }

}
