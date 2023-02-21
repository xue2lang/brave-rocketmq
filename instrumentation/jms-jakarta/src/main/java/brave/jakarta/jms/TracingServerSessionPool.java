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

import jakarta.jms.JMSException;
import jakarta.jms.ServerSession;
import jakarta.jms.ServerSessionPool;

final class TracingServerSessionPool implements ServerSessionPool {
  static ServerSessionPool create(ServerSessionPool delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("serverSessionPool == null");
    if (delegate instanceof TracingServerSessionPool) return delegate;
    return new TracingServerSessionPool(delegate, jmsTracing);
  }

  final ServerSessionPool delegate;
  final JmsTracing jmsTracing;

  TracingServerSessionPool(ServerSessionPool delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
  }

  @Override public ServerSession getServerSession() throws JMSException {
    return TracingServerSession.create(delegate.getServerSession(), jmsTracing);
  }
}
