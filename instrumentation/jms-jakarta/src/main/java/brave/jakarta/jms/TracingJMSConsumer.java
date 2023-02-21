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

import jakarta.jms.Destination;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;

final class TracingJMSConsumer extends TracingConsumer<JMSConsumer> implements JMSConsumer {
  final Destination destination;

  TracingJMSConsumer(JMSConsumer delegate, Destination destination, JmsTracing jmsTracing) {
    super(delegate, jmsTracing);
    this.destination = destination;
  }

  @Override Destination destination(Message message) {
    return destination;
  }

  @Override public String getMessageSelector() {
    return delegate.getMessageSelector();
  }

  @Override public MessageListener getMessageListener() throws JMSRuntimeException {
    return delegate.getMessageListener();
  }

  @Override public void setMessageListener(MessageListener listener) throws JMSRuntimeException {
    delegate.setMessageListener(TracingMessageListener.create(listener, jmsTracing));
  }

  @Override public Message receive() {
    Message message = delegate.receive();
    handleReceive(message);
    return message;
  }

  @Override public Message receive(long timeout) {
    Message message = delegate.receive(timeout);
    handleReceive(message);
    return message;
  }

  @Override public Message receiveNoWait() {
    Message message = delegate.receiveNoWait();
    handleReceive(message);
    return message;
  }

  @Override public void close() {
    delegate.close();
  }

  /**
   * This method will implicitly dispose of any incoming trace due to lack of obvious hooks to
   * continue it. It isn't enough to call {@link #receive()} followed by {@link
   * Message#getBody(Class)} because some implementations internally do ack tracking or even more.
   * It is possible that an "opt-in" utility to call this sequence could be made, in a way most
   * tracing could work, but this would need demand, and also there are problems further when you
   * consider the result is a plain java type. More on that below.
   *
   * The incoming trace could be terminated with a consumer span based on wrapping or
   * driver-specific hooks, but there would still be trouble continuing it further. For example, the
   * result is a type we won't know how to wrap or if we could wrap it. For example, the result type
   * could be a final class like String!
   *
   * In worst case we could setup a instance-to-tracecontext map to allow code to check for a
   * context based on the result of this method. However, if there is no implementation that can
   * intercept the receive part, this association would always be empty. If later we find a good way
   * to safely intercept, the association should definitely be bounded so as to not create OOM and
   * likely need to be weakly referenced to not hold references from being collected.
   */
  @Override public <T> T receiveBody(Class<T> c) {
    return delegate.receiveBody(c);
  }

  /** @see #receiveBody(Class) for explanation on why this isn't traced */
  @Override public <T> T receiveBody(Class<T> c, long timeout) {
    return delegate.receiveBody(c, timeout);
  }

  /** @see #receiveBody(Class) for explanation on why this isn't traced */
  @Override public <T> T receiveBodyNoWait(Class<T> c) {
    return delegate.receiveBodyNoWait(c);
  }
}
