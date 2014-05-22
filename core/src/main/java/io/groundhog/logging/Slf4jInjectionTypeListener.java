/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.groundhog.logging;

import com.google.inject.ProvisionException;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

/**
 * From https://github.com/dhanji/sitebricks/tree/master/slf4j/src/main/java/com/google/sitebricks/slf4j/.
 *
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class Slf4jInjectionTypeListener implements TypeListener {
  @Override
  public <I> void hear(final TypeLiteral<I> type, TypeEncounter<I> encounter) {
    final Field field = getLoggerField(type.getRawType());

    if (field != null) {
      encounter.register(new InjectionListener<I>() {
        @Override
        public void afterInjection(I injectee) {
          try {
            boolean b = field.isAccessible();
            if (!b) field.setAccessible(true);
            field.set(injectee,
                getLogger(type.getRawType()));
            if (!b) field.setAccessible(false);
          } catch (IllegalAccessException e) {
            throw new ProvisionException(
                "Unable to inject SLF4J logger", e);
          }
        }
      });
    }
  }

  protected Field getLoggerField(Class<?> clazz) {
    // search for Logger in current class and return it if found
    for (final Field field : clazz.getDeclaredFields()) {
      final Class<?> typeOfField = field.getType();
      if (Logger.class.isAssignableFrom(typeOfField)) {
        return field;
      }
    }

    // search for Logger in superclass if not found in this class
    if (clazz.getSuperclass() != null) {
      return getLoggerField(clazz.getSuperclass());
    }

    // not in current class and not having superclass, return null
    return null;
  }

  protected Logger getLogger(Class clazz) {
    return LoggerFactory.getLogger(clazz);
  }
}
