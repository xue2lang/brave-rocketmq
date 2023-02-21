# Brave JMS (Jakarta) instrumentation
This module provides instrumentation for Jakarta JMS 3.0+ consumers,
producers and listeners. It works by wrapping connections or connection
factories.

Under the scenes:
* `TracingMessageProducer` - completes a producer span per message and propagates it via headers.
* `TracingMessageConsumer` - completes a consumer span on `receive`, resuming a trace in headers if present.
  * The message returned has the current span encoded as the property "b3".
* `TracingMessageListener` - does the same as `TracingMessageConsumer`, and times the user-supplied listener.
  * The message passed to the listener has no "b3" header. Similar to a server, use `Tracer.currentSpan()`.


## Setup
First, setup the generic Jms component like this:
```java
jmsTracing = JmsTracing.newBuilder(messagingTracing)
                       .remoteServiceName("my-broker")
                       .build();
```

Now, just wrap your connection or connection factory
```java
ConnectionFactory tracingConnectionFactory = jmsTracing.connectionFactory(connectionFactory);

// When you later send a message, you'll notice "b3" as a message property
producer.send(message);
```

*NOTE* `JMSConsumer.receiveBodyXXX()` methods are not traced due to lack
of hooks. `JMSConsumer.receive()` followed by `Message.getBody()` is the
most compatible alternative. If you desire other routes, please raise an
issue or join https://gitter.im/openzipkin/zipkin

## Sampling Policy
The default sampling policy is to use the default (trace ID) sampler for
producer and consumer requests.

You can use an [MessagingRuleSampler](../messaging/README.md) to override this
based on JMS destination names.

Ex. Here's a sampler that traces 100 consumer requests per second, except for
the "alerts" topic. Other requests will use a global rate provided by the
`Tracing` component.

```java
import brave.sampler.Matchers;

import static brave.messaging.MessagingRequestMatchers.channelNameEquals;

messagingTracingBuilder.consumerSampler(MessagingRuleSampler.newBuilder()
  .putRule(channelNameEquals("alerts"), Sampler.NEVER_SAMPLE)
  .putRule(Matchers.alwaysMatch(), RateLimitingSampler.create(100))
  .build());

jmsTracing = JmsTracing.create(messagingTracing);
```

## What's happening?
Typically, there are three spans involved in message tracing:
* If a message producer is traced, it completes a PRODUCER span per message
* If a consumer is traced, receive completes a CONSUMER span based on the incoming queue or topic
* Message listener timing is in a child of the CONSUMER span.

## Custom Message Processing

If you are using Jms `MessageListener`, the following is automatic. If
you have custom code, or are using a different processing abstraction,
read below:

When ready for processing use `JmsTracing.nextSpan` to continue the trace.

```java
// Typically, poll is in a loop. the consumer is wrapped
while (running) {
  Message message = tracingMessageConsumer.receive();
  // either automatically or manually wrap your real process() method to use jmsTracing.nextSpan()
  messages.forEach(message -> process(message));
}
```

If you are in a position where you have a custom processing loop, you can do something like this
to trace manually or you can do similar via automatic instrumentation like AspectJ.
```java
void process(Message message) {
  // Grab any span from the queue. The queue or topic is automatically tagged
  Span span = jmsTracing.nextSpan(message).name("process").start();

  // Below is the same setup as any synchronous tracing
  try (SpanInScope ws = tracer.withSpanInScope(span)) { // so logging can see trace ID
    return doProcess(message); // do the actual work
  } catch (RuntimeException | Error e) {
    span.error(e); // make sure any error gets into the span before it is finished
    throw e;
  } finally {
    span.finish(); // ensure the span representing this processing completes.
  }
}
```

## Compatibility issues

There are known issues with ActiveMQ Artemis Client 2.x: 
 - the message property names have to be valid Java identifiers, as such properties like `X-B3-TraceId`, `X-B3-SpanId` will be rejected and not propagated
 - the message property of type `Object` only supports `String`, primitive wrappers (`Integer`, `Byte`, ...) and `byte[]`, other types be rejected and not set

## Troubleshooting
If you have problems with a JMS provider, such as broken traces, please capture the "FINE" output of
the Java logger: `brave.jakarta.jms.JmsTracing` and ask on [gitter](https://gitter.im/openzipkin/zipkin).

## Notes
* This instrumentation library works with Jakarta JMS 3.0+
* More information about "Message Tracing" [here](https://github.com/openzipkin/openzipkin.github.io/blob/master/pages/instrumenting.md#message-tracing)
