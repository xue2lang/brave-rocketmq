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

import java.io.Serializable;
import jakarta.jms.BytesMessage;
import jakarta.jms.ConnectionMetaData;
import jakarta.jms.Destination;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSProducer;
import jakarta.jms.MapMessage;
import jakarta.jms.Message;
import jakarta.jms.ObjectMessage;
import jakarta.jms.Queue;
import jakarta.jms.QueueBrowser;
import jakarta.jms.StreamMessage;
import jakarta.jms.TemporaryQueue;
import jakarta.jms.TemporaryTopic;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;
import jakarta.jms.XAJMSContext;

class TracingJMSContext implements JMSContext {
  static JMSContext create(JMSContext delegate, JmsTracing jmsTracing) {
    if (delegate == null) throw new NullPointerException("delegate == null");
    if (jmsTracing == null) throw new NullPointerException("jmsTracing == null");
    if (delegate instanceof XAJMSContext) {
      return new TracingXAJMSContext((XAJMSContext) delegate, jmsTracing);
    }
    return new TracingJMSContext(delegate, jmsTracing);
  }

  final JMSContext delegate;
  final JmsTracing jmsTracing;

  TracingJMSContext(JMSContext delegate, JmsTracing jmsTracing) {
    this.delegate = delegate;
    this.jmsTracing = jmsTracing;
    ExceptionListener original = delegate.getExceptionListener();
    if (original != null) {
      delegate.setExceptionListener(TracingExceptionListener.create(original, jmsTracing));
    } else {
      delegate.setExceptionListener(TracingExceptionListener.create(jmsTracing));
    }
  }

  @Override public JMSContext createContext(int sessionMode) {
    return new TracingJMSContext(delegate.createContext(sessionMode), jmsTracing);
  }

  @Override public JMSProducer createProducer() {
    return new TracingJMSProducer(delegate.createProducer(), jmsTracing);
  }

  @Override public String getClientID() {
    return delegate.getClientID();
  }

  @Override public void setClientID(String clientID) {
    delegate.setClientID(clientID);
  }

  @Override public ConnectionMetaData getMetaData() {
    return delegate.getMetaData();
  }

  @Override public ExceptionListener getExceptionListener() {
    return delegate.getExceptionListener();
  }

  @Override public void setExceptionListener(ExceptionListener listener) {
    delegate.setExceptionListener(TracingExceptionListener.create(listener, jmsTracing));
  }

  @Override public void start() {
    delegate.start();
  }

  @Override public void stop() {
    delegate.stop();
  }

  @Override public void setAutoStart(boolean autoStart) {
    delegate.setAutoStart(autoStart);
  }

  @Override public boolean getAutoStart() {
    return delegate.getAutoStart();
  }

  @Override public void close() {
    delegate.close();
  }

  @Override public BytesMessage createBytesMessage() {
    return delegate.createBytesMessage();
  }

  @Override public MapMessage createMapMessage() {
    return delegate.createMapMessage();
  }

  @Override public Message createMessage() {
    return delegate.createMessage();
  }

  @Override public ObjectMessage createObjectMessage() {
    return delegate.createObjectMessage();
  }

  @Override public ObjectMessage createObjectMessage(Serializable object) {
    return delegate.createObjectMessage(object);
  }

  @Override public StreamMessage createStreamMessage() {
    return delegate.createStreamMessage();
  }

  @Override public TextMessage createTextMessage() {
    return delegate.createTextMessage();
  }

  @Override public TextMessage createTextMessage(String text) {
    return delegate.createTextMessage(text);
  }

  @Override public boolean getTransacted() {
    return delegate.getTransacted();
  }

  @Override public int getSessionMode() {
    return delegate.getSessionMode();
  }

  @Override public void commit() {
    delegate.commit();
  }

  @Override public void rollback() {
    delegate.rollback();
  }

  @Override public void recover() {
    delegate.recover();
  }

  @Override public JMSConsumer createConsumer(Destination destination) {
    return new TracingJMSConsumer(delegate.createConsumer(destination), destination, jmsTracing);
  }

  @Override public JMSConsumer createConsumer(Destination destination, String messageSelector) {
    JMSConsumer cDelegate = delegate.createConsumer(destination, messageSelector);
    return new TracingJMSConsumer(cDelegate, destination, jmsTracing);
  }

  @Override public JMSConsumer createConsumer(Destination destination, String messageSelector,
    boolean noLocal) {
    JMSConsumer cDelegate = delegate.createConsumer(destination, messageSelector, noLocal);
    return new TracingJMSConsumer(cDelegate, destination, jmsTracing);
  }

  @Override public Queue createQueue(String queueName) {
    return delegate.createQueue(queueName);
  }

  @Override public Topic createTopic(String topicName) {
    return delegate.createTopic(topicName);
  }

  @Override public JMSConsumer createDurableConsumer(Topic topic, String name) {
    return new TracingJMSConsumer(delegate.createDurableConsumer(topic, name), topic, jmsTracing);
  }

  @Override
  public JMSConsumer createDurableConsumer(Topic topic, String name, String messageSelector,
    boolean noLocal) {
    JMSConsumer cDelegate = delegate.createDurableConsumer(topic, name, messageSelector, noLocal);
    return new TracingJMSConsumer(cDelegate, topic, jmsTracing);
  }

  @Override public JMSConsumer createSharedDurableConsumer(Topic topic, String name) {
    JMSConsumer cDelegate = delegate.createSharedDurableConsumer(topic, name);
    return new TracingJMSConsumer(cDelegate, topic, jmsTracing);
  }

  @Override
  public JMSConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) {
    JMSConsumer cDelegate = delegate.createSharedDurableConsumer(topic, name, messageSelector);
    return new TracingJMSConsumer(cDelegate, topic, jmsTracing);
  }

  @Override public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) {
    JMSConsumer cDelegate = delegate.createSharedConsumer(topic, sharedSubscriptionName);
    return new TracingJMSConsumer(cDelegate, topic, jmsTracing);
  }

  @Override public JMSConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName,
    String messageSelector) {
    JMSConsumer cDelegate =
      delegate.createSharedConsumer(topic, sharedSubscriptionName, messageSelector);
    return new TracingJMSConsumer(cDelegate, topic, jmsTracing);
  }

  @Override public QueueBrowser createBrowser(Queue queue) {
    return delegate.createBrowser(queue);
  }

  @Override public QueueBrowser createBrowser(Queue queue, String messageSelector) {
    return delegate.createBrowser(queue, messageSelector);
  }

  @Override public TemporaryQueue createTemporaryQueue() {
    return delegate.createTemporaryQueue();
  }

  @Override public TemporaryTopic createTemporaryTopic() {
    return delegate.createTemporaryTopic();
  }

  @Override public void unsubscribe(String name) {
    delegate.unsubscribe(name);
  }

  @Override public void acknowledge() {
    delegate.acknowledge();
  }
}
