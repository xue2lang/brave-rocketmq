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
import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.client.hook.ConsumeMessageHook;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * @author JoeKerouac
 * @date 2023-01-09 15:02
 */
public class ConsumeMessageBraveHookImpl implements ConsumeMessageHook {

  final RocketMQTracing tracing;

  public ConsumeMessageBraveHookImpl(RocketMQTracing tracing) {
    this.tracing = tracing;
  }

  @Override
  public String hookName() {
    return "ConsumeMessageBraveHook";
  }

  @Override
  public void consumeMessageBefore(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }

    List<MessageExt> msgList = context.getMsgList();
    List<Span> spanList = new ArrayList<>();
    for (MessageExt msg : msgList) {
      if (msg == null) {
        continue;
      }

      MessageConsumerRequest request = new MessageConsumerRequest(msg);

      Span span =
        SpanUtil.createAndStartSpan(tracing, tracing.consumerExtractor, tracing.consumerSampler,
          request, msg.getProperties());
      span.name(TraceConstants.FROM_PREFIX + msg.getTopic());
      span.tag(TraceConstants.ROCKETMQ_MSG_ID, msg.getMsgId());
      span.tag(TraceConstants.ROCKETMQ_TAGS, StringUtils.getOrEmpty(msg.getTags()));
      span.tag(TraceConstants.ROCKETMQ_TRANSACTION_ID,
        StringUtils.getOrEmpty(msg.getTransactionId()));
      span.tag(TraceConstants.ROCKETMQ_KEYS, StringUtils.getOrEmpty(msg.getKeys()));
      span.tag(TraceConstants.ROCKETMQ_BODY_LENGTH, Integer.toString(msg.getStoreSize()));
      span.tag(TraceConstants.ROCKETMQ_RETRY_TIMERS, Integer.toString(msg.getReconsumeTimes()));
      span.tag(TraceConstants.ROCKETMQ_REGION_ID,
        StringUtils.getOrEmpty(msg.getProperty(MessageConst.PROPERTY_MSG_REGION)));
      span.tag(TraceConstants.ROCKETMQ_CONSUMER_GROUP, context.getConsumerGroup());
      spanList.add(span);
    }

    context.setMqTraceContext(spanList);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void consumeMessageAfter(ConsumeMessageContext context) {
    if (context == null || context.getMsgList() == null || context.getMsgList().isEmpty()) {
      return;
    }

    List<Span> spanList = (List<Span>) context.getMqTraceContext();
    if (spanList == null || spanList.isEmpty()) {
      return;
    }

    for (Span span : spanList) {
      span.tag(TraceConstants.ROCKETMQ_SUCCESS, Boolean.toString(context.isSuccess()));
      long timestamp = tracing.tracing.clock(span.context()).currentTimeMicroseconds();
      span.finish(timestamp);
    }
  }
}
