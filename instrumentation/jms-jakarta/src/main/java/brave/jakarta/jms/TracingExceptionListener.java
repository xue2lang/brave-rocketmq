/*
 * Copyright 2013-2023 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.jakarta.jms;

import brave.Span;
import brave.Tracer;
import brave.Tracer.SpanInScope;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSException;

final class TracingExceptionListener {
  static ExceptionListener create(JmsTracing jmsTracing) {
    return new TagError(jmsTracing.tracing.tracer());
  }

  static ExceptionListener create(ExceptionListener delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("exceptionListener == null");
    if (delegate instanceof TagError) return delegate;
    return new DelegateAndTagError(delegate, jmsTracing.tracing.tracer());
  }

  static class TagError implements ExceptionListener {
    final Tracer tracer;

    TagError(Tracer tracer) {
      this.tracer = tracer;
    }

    @Override public void onException(JMSException exception) {
      Span span = tracer.currentSpan();
      if (span != null) span.error(exception);
    }
  }

  static final class DelegateAndTagError extends TagError {
    final ExceptionListener delegate;

    DelegateAndTagError(ExceptionListener delegate, Tracer tracer) {
      super(tracer);
      this.delegate = delegate;
    }

    @Override public void onException(JMSException exception) {
      Span span = tracer.currentSpan();
      if (span == null) {
        delegate.onException(exception);
        return;
      }
      try (SpanInScope ws = tracer.withSpanInScope(span)) {
        delegate.onException(exception);
      } finally {
        span.error(exception);
      }
    }
  }
}
