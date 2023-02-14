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
import brave.Tracer;
import brave.Tracing;
import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContextOrSamplingFlags;
import brave.sampler.SamplerFunction;
import java.util.Map;

/**
 * @author JoeKerouac
 * @date 2023-01-09 15:20
 */
public class RocketMQTracing {

  public static RocketMQTracing create(Tracing tracing) {
    return newBuilder(tracing).build();
  }

  public static RocketMQTracing create(MessagingTracing messagingTracing) {
    return newBuilder(messagingTracing).build();
  }

  public static Builder newBuilder(Tracing tracing) {
    return newBuilder(MessagingTracing.create(tracing));
  }

  public static Builder newBuilder(MessagingTracing messagingTracing) {
    return new Builder(messagingTracing);
  }

  public static final class Builder {
    final MessagingTracing messagingTracing;
    String remoteServiceName = TraceConstants.ROCKETMQ_SERVICE;

    Builder(MessagingTracing messagingTracing) {
      if (messagingTracing == null) throw new NullPointerException("messagingTracing == null");

      this.messagingTracing = messagingTracing;
    }

    /**
     * The remote service name that describes the broker in the dependency graph. Defaults to "rocketmq"
     */
    public Builder remoteServiceName(String remoteServiceName) {
      this.remoteServiceName = remoteServiceName;
      return this;
    }

    public RocketMQTracing build() {
      return new RocketMQTracing(this);
    }
  }

  final Tracing tracing;
  final Tracer tracer;
  final TraceContext.Extractor<MessageProducerRequest> producerExtractor;
  final TraceContext.Extractor<MessageConsumerRequest> consumerExtractor;
  final TraceContext.Injector<MessageProducerRequest> producerInjector;
  final TraceContext.Injector<MessageConsumerRequest> consumerInjector;
  final String[] traceIdHeaders;
  final SamplerFunction<MessagingRequest> producerSampler, consumerSampler;
  final String remoteServiceName;

  RocketMQTracing(Builder builder) { // intentionally hidden constructor
    this.tracing = builder.messagingTracing.tracing();
    this.tracer = tracing.tracer();
    MessagingTracing messagingTracing = builder.messagingTracing;
    Propagation<String> propagation = messagingTracing.propagation();
    this.producerExtractor = propagation.extractor(MessageProducerRequest.GETTER);
    this.consumerExtractor = propagation.extractor(MessageConsumerRequest.GETTER);
    this.producerInjector = propagation.injector(MessageProducerRequest.SETTER);
    this.consumerInjector = propagation.injector(MessageConsumerRequest.SETTER);
    this.producerSampler = messagingTracing.producerSampler();
    this.consumerSampler = messagingTracing.consumerSampler();
    this.remoteServiceName = builder.remoteServiceName;

    // We clear the trace ID headers, so that a stale consumer span is not preferred over current
    // listener. We intentionally don't clear BaggagePropagation.allKeyNames as doing so will
    // application fields "user_id" or "country_code"
    this.traceIdHeaders = propagation.keys().toArray(new String[0]);
  }

  <R> TraceContextOrSamplingFlags extractAndClearTraceIdHeaders(TraceContext.Extractor<R> extractor,
    R request,
    Map<String, String> properties) {
    TraceContextOrSamplingFlags extracted = extractor.extract(request);
    // Clear any propagation keys present in the headers
    if (extracted.samplingFlags() == null) { // then trace IDs were extracted
      if (properties != null) {
        clearTraceIdHeaders(properties);
      }
    }
    return extracted;
  }

  /**
   * Creates a potentially noop remote span representing this request
   */
  Span nextMessagingSpan(SamplerFunction<MessagingRequest> sampler, MessagingRequest request,
    TraceContextOrSamplingFlags extracted) {
    Boolean sampled = extracted.sampled();
    // only recreate the context if the messaging sampler made a decision
    if (sampled == null && (sampled = sampler.trySample(request)) != null) {
      extracted = extracted.sampled(sampled.booleanValue());
    }
    return tracer.nextSpan(extracted);
  }

  // We can't just skip clearing headers we use because we might inject B3 single, yet have stale B3
  // multi, or visa versa.
  void clearTraceIdHeaders(Map<String, String> headers) {
    for (String traceIDHeader : traceIdHeaders)
      headers.remove(traceIDHeader);
  }
}
