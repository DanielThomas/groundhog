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

package io.groundhog.jmeter;

import io.groundhog.logging.Slf4jInjectionTypeListener;

import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Priority;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * Injects SLF4J compatible {@link org.slf4j.Logger}s, that adapt logging events to the JMeter logging framework.
 *
 * @author Danny Thomas
 * @see io.groundhog.logging.Slf4jInjectionTypeListener
 * @since 1.0
 */
public class JMeterSlf4jInjectionTypeListener extends Slf4jInjectionTypeListener {
  @Override
  protected Logger getLogger(Class clazz) {
    return new JMeterSlf4jLoggerAdapter(clazz);
  }

  private static final class JMeterSlf4jLoggerAdapter implements Logger {
    private final String name;
    private final org.apache.log.Logger log;

    JMeterSlf4jLoggerAdapter(Class clazz) {
      name = clazz.getName();
      log = LoggingManager.getLoggerFor(name);
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isTraceEnabled() {
      return log.isDebugEnabled();
    }

    private void log(Priority priority, String msg) {
      log.log(priority, msg);
    }

    private void log(Priority priority, String format, Object arg) {
      log.log(priority, MessageFormatter.format(format, arg).getMessage());
    }

    private void log(Priority priority, String format, Object arg1, Object arg2) {
      log.log(priority, MessageFormatter.format(format, arg1, arg2).getMessage());
    }

    private void log(Priority priority, String format, Object... arguments) {
      log.log(priority, MessageFormatter.arrayFormat(format, arguments).getMessage());
    }

    private void log(Priority priority, String msg, Throwable t) {
      log.log(priority, msg, t);
    }

    @Override
    public void trace(String msg) {
      log(Priority.DEBUG, msg);
    }

    @Override
    public void trace(String format, Object arg) {
      log(Priority.DEBUG, format, arg);
    }

    @Override
    public void trace(String format, Object arg1, Object arg2) {
      log(Priority.DEBUG, format, arg1, arg2);
    }

    @Override
    public void trace(String format, Object... arguments) {
      log(Priority.DEBUG, format, arguments);
    }

    @Override
    public void trace(String msg, Throwable t) {
      log(Priority.DEBUG, msg, t);
    }

    @Override
    public boolean isTraceEnabled(Marker marker) {
      return log.isDebugEnabled();
    }

    @Override
    public void trace(Marker marker, String msg) {
      trace(msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg) {
      trace(format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2) {
      trace(format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray) {
      trace(format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t) {
      trace(msg, t);
    }

    @Override
    public boolean isDebugEnabled() {
      return log.isDebugEnabled();
    }

    @Override
    public void debug(String msg) {
      trace(msg);
    }

    @Override
    public void debug(String format, Object arg) {
      trace(format, arg);
    }

    @Override
    public void debug(String format, Object arg1, Object arg2) {
      trace(format, arg1, arg2);
    }

    @Override
    public void debug(String format, Object... arguments) {
      trace(format, arguments);
    }

    @Override
    public void debug(String msg, Throwable t) {
      trace(msg, t);
    }

    @Override
    public boolean isDebugEnabled(Marker marker) {
      return isDebugEnabled();
    }

    @Override
    public void debug(Marker marker, String msg) {
      trace(msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg) {
      trace(format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2) {
      trace(format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments) {
      trace(format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t) {
      trace(msg, t);
    }

    @Override
    public boolean isInfoEnabled() {
      return log.isInfoEnabled();
    }

    @Override
    public void info(String msg) {
      log(Priority.INFO, msg);
    }

    @Override
    public void info(String format, Object arg) {
      log(Priority.INFO, format, arg);
    }

    @Override
    public void info(String format, Object arg1, Object arg2) {
      log(Priority.INFO, format, arg1, arg2);
    }

    @Override
    public void info(String format, Object... arguments) {
      log(Priority.INFO, format, arguments);
    }

    @Override
    public void info(String msg, Throwable t) {
      log(Priority.INFO, msg, t);
    }

    @Override
    public boolean isInfoEnabled(Marker marker) {
      return isInfoEnabled();
    }

    @Override
    public void info(Marker marker, String msg) {
      info(msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg) {
      info(format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2) {
      info(format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments) {
      info(format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t) {
      info(msg, t);
    }

    @Override
    public boolean isWarnEnabled() {
      return log.isWarnEnabled();
    }

    @Override
    public void warn(String msg) {
      log(Priority.WARN, msg);
    }

    @Override
    public void warn(String format, Object arg) {
      log(Priority.WARN, format, arg);
    }

    @Override
    public void warn(String format, Object... arguments) {
      log(Priority.WARN, format, arguments);
    }

    @Override
    public void warn(String format, Object arg1, Object arg2) {
      log(Priority.WARN, format, arg1, arg2);
    }

    @Override
    public void warn(String msg, Throwable t) {
      log(Priority.WARN, msg, t);
    }

    @Override
    public boolean isWarnEnabled(Marker marker) {
      return isWarnEnabled();
    }

    @Override
    public void warn(Marker marker, String msg) {
      warn(msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg) {
      warn(format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2) {
      warn(format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments) {
      warn(format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t) {
      warn(msg, t);
    }

    @Override
    public boolean isErrorEnabled() {
      return log.isErrorEnabled();
    }

    @Override
    public void error(String msg) {
      log(Priority.ERROR, msg);
    }

    @Override
    public void error(String format, Object arg) {
      log(Priority.ERROR, format, arg);
    }

    @Override
    public void error(String format, Object arg1, Object arg2) {
      log(Priority.ERROR, format, arg1, arg2);
    }

    @Override
    public void error(String format, Object... arguments) {
      log(Priority.ERROR, format, arguments);
    }

    @Override
    public void error(String msg, Throwable t) {
      log(Priority.ERROR, msg, t);
    }

    @Override
    public boolean isErrorEnabled(Marker marker) {
      return isErrorEnabled();
    }

    @Override
    public void error(Marker marker, String msg) {
      error(msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg) {
      error(format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2) {
      error(format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments) {
      error(format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t) {
      error(msg, t);
    }
  }
}
