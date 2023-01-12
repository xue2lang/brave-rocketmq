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
package brave.rocketmq.clients;

import brave.Span;
import brave.handler.MutableSpan;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.junit.Assert;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author JoeKerouac
 * @date 2023-01-11 11:45
 */
public class ITRocketMQTracing extends ITRocketMQ {

  @Test
  public void testSend() throws Exception {
    String topic = TOPIC_PREFIX + "testSend";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSend");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new SendMessageBraveHookImpl(producerTracing));
    producer.setNamesrvAddr(nameserver.getContainerIpAddress() + ":" + NAMESERVER_PORT);
    producer.start();
    producer.send(message);

    producer.shutdown();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test
  public void testSendOneway() throws Exception {
    String topic = TOPIC_PREFIX + "testSendOneway";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSendOneway");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new SendMessageBraveHookImpl(producerTracing));
    producer.setNamesrvAddr(nameserver.getContainerIpAddress() + ":" + NAMESERVER_PORT);
    producer.start();
    producer.sendOneway(message);

    producer.shutdown();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test
  public void testSendAsync() throws Exception {
    String topic = TOPIC_PREFIX + "testSendAsync";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    DefaultMQProducer producer = new DefaultMQProducer("testSendAsync");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new SendMessageBraveHookImpl(producerTracing));
    producer.setNamesrvAddr(nameserver.getContainerIpAddress() + ":" + NAMESERVER_PORT);
    producer.start();
    CountDownLatch latch = new CountDownLatch(1);
    producer.send(message, new SendCallback() {
      @Override public void onSuccess(SendResult sendResult) {
        latch.countDown();
      }

      @Override public void onException(Throwable e) {

      }
    });

    Assert.assertTrue(latch.await(3000, TimeUnit.MILLISECONDS));
    producer.shutdown();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
  }

  @Test
  public void testPullConsumer() throws Exception {
    String topic = TOPIC_PREFIX + "testPullConsumer";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    String nameserverAddr = nameserver.getContainerIpAddress() + ":" + NAMESERVER_PORT;
    DefaultMQProducer producer = new DefaultMQProducer("testPullConsumer");
    producer.setNamesrvAddr(nameserverAddr);
    producer.start();

    DefaultMQPullConsumer consumer = new DefaultMQPullConsumer("testPullConsumer");
    consumer.getDefaultMQPullConsumerImpl()
      .registerConsumeMessageHook(new ConsumeMessageBraveHookImpl(consumerTracing));
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.start();
    producer.send(message);

    Set<MessageQueue> messageQueues = consumer.fetchSubscribeMessageQueues(topic);
    for (MessageQueue messageQueue : messageQueues) {
      PullResult pull = consumer.pull(messageQueue, "*", 0, 1);
      if (pull.getPullStatus() == PullStatus.FOUND) {
        break;
      }
    }

    producer.shutdown();
    consumer.shutdown();

    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(consumerSpan.parentId()).isNull();
  }

  @Test
  public void testPushConsumer() throws Exception {
    String topic = TOPIC_PREFIX + "testPushConsumer";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    String nameserverAddr = nameserver.getContainerIpAddress() + ":" + NAMESERVER_PORT;
    DefaultMQProducer producer = new DefaultMQProducer("testPushConsumer");
    producer.setNamesrvAddr(nameserverAddr);
    producer.start();

    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testPushConsumer");
    consumer.getDefaultMQPushConsumerImpl()
      .registerConsumeMessageHook(new ConsumeMessageBraveHookImpl(consumerTracing));
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    CountDownLatch latch = new CountDownLatch(1);
    consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
      latch.countDown();
      return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    });
    consumer.start();

    producer.send(message);
    Assert.assertTrue(latch.await(3000, TimeUnit.MILLISECONDS));
    producer.shutdown();
    consumer.shutdown();

    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(consumerSpan.parentId()).isNull();
  }

  @Test
  public void testAll() throws Exception {
    String topic = TOPIC_PREFIX + "testAll";
    Message message = new Message(topic, "JoeKerouac", "hello".getBytes());
    String nameserverAddr = nameserver.getContainerIpAddress() + ":" + NAMESERVER_PORT;
    DefaultMQProducer producer = new DefaultMQProducer("testAll");
    producer.getDefaultMQProducerImpl()
      .registerSendMessageHook(new SendMessageBraveHookImpl(producerTracing));
    producer.setNamesrvAddr(nameserverAddr);
    producer.start();

    DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("testAll");
    consumer.getDefaultMQPushConsumerImpl()
      .registerConsumeMessageHook(new ConsumeMessageBraveHookImpl(consumerTracing));
    consumer.setNamesrvAddr(nameserverAddr);
    consumer.subscribe(topic, "*");
    CountDownLatch latch = new CountDownLatch(1);
    consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
      latch.countDown();
      return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    });
    consumer.start();

    producer.send(message);
    Assert.assertTrue(latch.await(3000, TimeUnit.MILLISECONDS));

    producer.shutdown();
    consumer.shutdown();

    MutableSpan producerSpan = producerSpanHandler.takeRemoteSpan(Span.Kind.PRODUCER);
    assertThat(producerSpan.parentId()).isNull();
    MutableSpan consumerSpan = consumerSpanHandler.takeRemoteSpan(Span.Kind.CONSUMER);
    assertThat(consumerSpan.parentId()).isNotNull();
    assertChildOf(consumerSpan, producerSpan);
  }
}
