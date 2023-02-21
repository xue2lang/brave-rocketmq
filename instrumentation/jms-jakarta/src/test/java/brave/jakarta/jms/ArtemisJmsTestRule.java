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

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.jms.Connection;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.QueueConnection;
import jakarta.jms.TopicConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQJMSConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQMessage;
import org.apache.activemq.artemis.junit.EmbeddedActiveMQResource;
import org.junit.rules.TestName;

/**
 * Currently, regular activemq doesn't support JMS 2.0, so we need to use the one that requires
 * netty etc.
 *
 * <p>See https://issues.apache.org/jira/browse/AMQ-5736?focusedCommentId=16593091&page=com.atlassian.jira.plugin.system.issuetabpanels%3Acomment-tabpanel#comment-16593091
 */
class ArtemisJmsTestRule extends JmsTestRule {
  EmbeddedActiveMQResource resource = new EmbeddedActiveMQResource();
  ActiveMQJMSConnectionFactory factory;
  AtomicBoolean started = new AtomicBoolean();

  ArtemisJmsTestRule(TestName testName) {
    super(testName);
    factory = new ActiveMQJMSConnectionFactory("vm://0");
    factory.setProducerMaxRate(1); // to allow tests to use production order
  }

  JMSContext newContext() {
    return factory.createContext(JMSContext.AUTO_ACKNOWLEDGE);
  }

  @Override Connection newConnection() throws JMSException {
    if (!started.getAndSet(true)) resource.start();
    return factory.createConnection();
  }

  @Override QueueConnection newQueueConnection() throws JMSException {
    if (!started.getAndSet(true)) resource.start();
    return factory.createQueueConnection();
  }

  @Override TopicConnection newTopicConnection() throws JMSException {
    if (!started.getAndSet(true)) resource.start();
    return factory.createTopicConnection();
  }

  @Override void setReadOnlyProperties(Message message, boolean readOnlyProperties) {
    try {
      Field propertiesReadOnly = ActiveMQMessage.class.getDeclaredField("propertiesReadOnly");
      propertiesReadOnly.setAccessible(true);
      propertiesReadOnly.set(message, readOnlyProperties);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Override public void after() {
    super.after();
    factory.close();
    resource.stop();
  }
}
