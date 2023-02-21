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

import brave.Tags;
import brave.handler.MutableSpan;
import brave.messaging.MessagingRuleSampler;
import brave.messaging.MessagingTracing;
import brave.propagation.B3SingleFormat;
import brave.propagation.SamplingFlags;
import brave.propagation.TraceContext;
import brave.sampler.Sampler;
import jakarta.jms.BytesMessage;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.QueueReceiver;
import jakarta.jms.QueueSender;
import jakarta.jms.QueueSession;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.TopicPublisher;
import jakarta.jms.TopicSession;
import jakarta.jms.TopicSubscriber;
import jakarta.jms.JMSConsumer;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import static brave.Span.Kind.CONSUMER;
import static brave.jakarta.jms.MessageProperties.getPropertyIfString;
import static brave.jakarta.jms.MessageProperties.setStringProperty;
import static brave.messaging.MessagingRequestMatchers.channelNameEquals;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.Map;

/** When adding tests here, also add to {@linkplain brave.jms.ITTracingJMSConsumer} */
public class ITTracingMessageConsumer extends ITJms {
  @Rule public TestName testName = new TestName();
  @Rule public JmsTestRule jms = newJmsTestRule(testName);

  Session tracedSession;
  MessageProducer messageProducer;
  MessageConsumer messageConsumer;

  QueueSession tracedQueueSession;
  QueueSender queueSender;
  QueueReceiver queueReceiver;

  TopicSession tracedTopicSession;
  TopicPublisher topicPublisher;
  TopicSubscriber topicSubscriber;

  JmsTestRule newJmsTestRule(TestName testName) {
    return new ArtemisJmsTestRule(testName);
  }

  TextMessage message;
  BytesMessage bytesMessage;

  @Before public void setup() throws JMSException {
    tracedSession = jmsTracing.connection(jms.connection)
      .createSession(false, Session.AUTO_ACKNOWLEDGE);
    tracedQueueSession = jmsTracing.queueConnection(jms.queueConnection)
      .createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
    tracedTopicSession = jmsTracing.topicConnection(jms.topicConnection)
      .createTopicSession(false, Session.AUTO_ACKNOWLEDGE);

    messageProducer = jms.session.createProducer(jms.destination);
    messageConsumer = tracedSession.createConsumer(jms.destination);

    queueSender = jms.queueSession.createSender(jms.queue);
    queueReceiver = tracedQueueSession.createReceiver(jms.queue);

    topicPublisher = jms.topicSession.createPublisher(jms.topic);
    topicSubscriber = tracedTopicSession.createSubscriber(jms.topic);

    message = jms.newMessage("foo");
    bytesMessage = jms.newBytesMessage("foo");
    lockMessages();
  }

  void lockMessages() throws JMSException {
    // this forces us to handle JMS write concerns!
    jms.setReadOnlyProperties(message, true);
    jms.setReadOnlyProperties(bytesMessage, true);
    bytesMessage.reset();
  }

  TraceContext resetB3PropertyWithNewSampledContext(JmsTestRule jms) throws JMSException {
    TraceContext parent = newTraceContext(SamplingFlags.SAMPLED);
    message = jms.newMessage("foo");
    bytesMessage = jms.newBytesMessage("foo");
    String b3 = B3SingleFormat.writeB3SingleFormatWithoutParentId(parent);
    setStringProperty(message, "b3", b3);
    setStringProperty(bytesMessage, "b3", b3);
    lockMessages();
    return parent;
  }

  @After public void tearDownTraced() throws JMSException {
    tracedSession.close();
    tracedQueueSession.close();
    tracedTopicSession.close();
  }

  @Test public void messageListener_runsAfterConsumer() throws JMSException {
    messageListener_runsAfterConsumer(() -> messageProducer.send(message), messageConsumer);
  }

  @Test public void messageListener_runsAfterConsumer_queue() throws JMSException {
    messageListener_runsAfterConsumer(() -> queueSender.send(message), queueReceiver);
  }

  @Test public void messageListener_runsAfterConsumer_topic() throws JMSException {
    messageListener_runsAfterConsumer(() -> topicPublisher.send(message), topicSubscriber);
  }

  void messageListener_runsAfterConsumer(JMSRunnable send, MessageConsumer messageConsumer)
    throws JMSException {
    messageConsumer.setMessageListener(m -> {
    });
    send.run();

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();
    assertChildOf(listenerSpan, consumerSpan);
    assertSequential(consumerSpan, listenerSpan);
  }

  @Test public void messageListener_startsNewTrace() throws JMSException {
    messageListener_startsNewTrace(
      () -> messageProducer.send(message),
      messageConsumer,
      Collections.singletonMap("jms.queue", jms.destinationName)
    );
  }

  @Test public void messageListener_startsNewTrace_bytes() throws JMSException {
    messageListener_startsNewTrace(
      () -> messageProducer.send(bytesMessage),
      messageConsumer,
      Collections.singletonMap("jms.queue", jms.destinationName)
    );
  }

  @Test public void messageListener_startsNewTrace_queue() throws JMSException {
    messageListener_startsNewTrace(
      () -> queueSender.send(message),
      queueReceiver,
      Collections.singletonMap("jms.queue", jms.queueName)
    );
  }

  @Test public void messageListener_startsNewTrace_topic() throws JMSException {
    messageListener_startsNewTrace(
      () -> topicPublisher.send(message),
      topicSubscriber,
      Collections.singletonMap("jms.topic", jms.topicName)
    );
  }

  void messageListener_startsNewTrace(JMSRunnable send, MessageConsumer messageConsumer,
    Map<String, String> consumerTags) throws JMSException {
    messageConsumer.setMessageListener(
      m -> {
        tracing.tracer().currentSpanCustomizer().name("message-listener");

        // clearing headers ensures later work doesn't try to use the old parent
        String b3 = getPropertyIfString(m, "b3");
        tracing.tracer().currentSpanCustomizer().tag("b3", String.valueOf(b3 != null));
      }
    );
    send.run();

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.tags()).containsAllEntriesOf(consumerTags);

    assertChildOf(listenerSpan, consumerSpan);
    assertThat(listenerSpan.name()).isEqualTo("message-listener"); // overridden name
    assertThat(listenerSpan.tags())
      .hasSize(1) // no redundant copy of consumer tags
      .containsEntry("b3", "false"); // b3 header not leaked to listener
  }

  @Test public void messageListener_resumesTrace() throws JMSException {
    messageListener_resumesTrace(() -> messageProducer.send(message), messageConsumer);
  }

  @Test public void messageListener_resumesTrace_bytes() throws JMSException {
    messageListener_resumesTrace(() -> messageProducer.send(bytesMessage), messageConsumer);
  }

  @Test public void messageListener_resumesTrace_queue() throws JMSException {
    messageListener_resumesTrace(() -> queueSender.send(message), queueReceiver);
  }

  @Test public void messageListener_resumesTrace_topic() throws JMSException {
    messageListener_resumesTrace(() -> topicPublisher.send(message), topicSubscriber);
  }

  void messageListener_resumesTrace(JMSRunnable send, MessageConsumer messageConsumer)
    throws JMSException {
    messageConsumer.setMessageListener(m -> {
        // clearing headers ensures later work doesn't try to use the old parent
        String b3 = getPropertyIfString(m, "b3");
        tracing.tracer().currentSpanCustomizer().tag("b3", String.valueOf(b3 != null));
      }
    );

    TraceContext parent = resetB3PropertyWithNewSampledContext(jms);
    send.run();

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertChildOf(consumerSpan, parent);
    assertChildOf(listenerSpan, consumerSpan);
    assertThat(listenerSpan.tags())
      .hasSize(1) // no redundant copy of consumer tags
      .containsEntry("b3", "false"); // b3 header not leaked to listener
  }

  @Test public void messageListener_readsBaggage() throws JMSException {
    messageListener_readsBaggage(() -> messageProducer.send(message), messageConsumer);
  }

  @Test public void messageListener_readsBaggage_bytes() throws JMSException {
    messageListener_readsBaggage(() -> messageProducer.send(bytesMessage), messageConsumer);
  }

  @Test public void messageListener_readsBaggage_queue() throws JMSException {
    messageListener_readsBaggage(() -> queueSender.send(message), queueReceiver);
  }

  @Test public void messageListener_readsBaggage_topic() throws JMSException {
    messageListener_readsBaggage(() -> topicPublisher.send(message), topicSubscriber);
  }

  void messageListener_readsBaggage(JMSRunnable send, MessageConsumer messageConsumer)
      throws JMSException {
    messageConsumer.setMessageListener(m ->
        Tags.BAGGAGE_FIELD.tag(BAGGAGE_FIELD, tracing.tracer().currentSpan())
    );

    message = jms.newMessage("baggage");
    bytesMessage = jms.newBytesMessage("baggage");
    String baggage = "joey";
    setStringProperty(message, BAGGAGE_FIELD_KEY, baggage);
    setStringProperty(bytesMessage, BAGGAGE_FIELD_KEY, baggage);
    lockMessages();
    send.run();

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    MutableSpan listenerSpan = testSpanHandler.takeLocalSpan();

    assertThat(consumerSpan.parentId()).isNull();
    assertChildOf(listenerSpan, consumerSpan);
    assertThat(listenerSpan.tags())
        .containsEntry(BAGGAGE_FIELD.name(), baggage);
  }

  @Test public void receive_startsNewTrace() throws JMSException {
    receive_startsNewTrace(
      () -> messageProducer.send(message),
      messageConsumer,
      Collections.singletonMap("jms.queue", jms.destinationName)
    );
  }

  @Test public void receive_startsNewTrace_queue() throws JMSException {
    receive_startsNewTrace(
      () -> queueSender.send(message),
      queueReceiver,
      Collections.singletonMap("jms.queue", jms.queueName)
    );
  }

  @Test public void receive_startsNewTrace_topic() throws JMSException {
    receive_startsNewTrace(
      () -> topicPublisher.send(message),
      topicSubscriber,
      Collections.singletonMap("jms.topic", jms.topicName)
    );
  }

  void receive_startsNewTrace(JMSRunnable send, MessageConsumer messageConsumer,
    Map<String, String> consumerTags) throws JMSException {
    send.run();

    messageConsumer.receive();

    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertThat(consumerSpan.name()).isEqualTo("receive");
    assertThat(consumerSpan.parentId()).isNull(); // root span
    assertThat(consumerSpan.tags()).containsAllEntriesOf(consumerTags);
  }

  @Test public void receive_resumesTrace() throws JMSException {
    receive_resumesTrace(() -> messageProducer.send(message), messageConsumer);
  }

  @Test public void receive_resumesTrace_queue() throws JMSException {
    receive_resumesTrace(() -> queueSender.send(message), queueReceiver);
  }

  @Test public void receive_resumesTrace_topic() throws JMSException {
    receive_resumesTrace(() -> topicPublisher.send(message), topicSubscriber);
  }

  void receive_resumesTrace(JMSRunnable send, MessageConsumer messageConsumer) throws JMSException {
    TraceContext parent = resetB3PropertyWithNewSampledContext(jms);
    send.run();

    Message received = messageConsumer.receive();
    MutableSpan consumerSpan = testSpanHandler.takeRemoteSpan(CONSUMER);
    assertChildOf(consumerSpan, parent);

    assertThat(received.getStringProperty("b3"))
      .isEqualTo(parent.traceIdString() + "-" + consumerSpan.id() + "-1");
  }

  // Inability to encode "b3" on a received BytesMessage only applies to ActiveMQ 5.x
  @Test public void receive_resumesTrace_bytes() throws JMSException {
    receive_resumesTrace(() -> messageProducer.send(bytesMessage), messageConsumer);
  }

  @Test public void receive_customSampler() throws JMSException {
    queueReceiver.close();

    MessagingRuleSampler consumerSampler = MessagingRuleSampler.newBuilder()
      .putRule(channelNameEquals(jms.queue.getQueueName()), Sampler.NEVER_SAMPLE)
      .build();

    try (MessagingTracing messagingTracing = MessagingTracing.newBuilder(tracing)
      .consumerSampler(consumerSampler)
      .build();
         JMSContext context = JmsTracing.create(messagingTracing)
           .connectionFactory(((ArtemisJmsTestRule) jms).factory)
           .createContext(JMSContext.AUTO_ACKNOWLEDGE);
         JMSConsumer consumer = context.createConsumer(jms.queue)
    ) {
      queueSender.send(message);

      // Check that the message headers are not sampled
      assertThat(consumer.receive().getStringProperty("b3"))
        .endsWith("-0");
    }

    // @After will also check that the consumer was not sampled
  }
}
