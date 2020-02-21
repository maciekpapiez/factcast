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
package org.factcast.store.pgsql.validation;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.LinkedList;

import org.aspectj.lang.ProceedingJoinPoint;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.junit.jupiter.api.Test;

public class FactValidationAspectTest {

    ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

    FactValidator v = mock(FactValidator.class);

    FactValidationAspect uut = new FactValidationAspect(v);

    Fact f = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();

    @Test
    void testInterceptPublish() throws Throwable {

        when(jp.getArgs()).thenReturn(new Object[] { Collections.singletonList(f) });
        when(v.validate(f)).thenReturn(new LinkedList());

        Object interceptPublish = uut.interceptPublish(jp);

        verify(jp).proceed();
    }

    @Test
    void testInterceptPublishConditional() throws Throwable {

        when(jp.getArgs()).thenReturn(new Object[] { Collections.singletonList(f) });
        when(v.validate(f)).thenReturn(new LinkedList());

        Object interceptPublish = uut.interceptPublishIfUnchanged(jp);

        verify(jp).proceed();
    }

    @Test
    void testInterceptPublishPropagatesErros() throws Throwable {

        when(jp.getArgs()).thenReturn(new Object[] { Collections.singletonList(f) });
        when(v.validate(f)).thenReturn(Collections.singletonList(new FactValidationError("doing")));

        try {
            Object interceptPublish = uut.interceptPublish(jp);
            fail();
        } catch (FactValidationException e) {
            // expected
        }
        verify(jp, never()).proceed();
    }
}