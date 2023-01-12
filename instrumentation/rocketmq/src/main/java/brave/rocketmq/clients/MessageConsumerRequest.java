/*
 * Copyright 2013-2023 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package brave.rocketmq.clients;

import brave.Span;
import brave.messaging.ConsumerRequest;
import brave.propagation.Propagation;
import org.apache.rocketmq.common.message.MessageExt;

/**
 * @author JoeKerouac
 * @date 2023-01-09 16:28
 */
final class MessageConsumerRequest extends ConsumerRequest {

  static final Propagation.RemoteGetter<MessageConsumerRequest> GETTER =
    new Propagation.RemoteGetter<MessageConsumerRequest>() {
      @Override
      public Span.Kind spanKind() {
        return Span.Kind.CONSUMER;
      }

      @Override
      public String get(MessageConsumerRequest request, String name) {
        return request.delegate.getUserProperty(name);
      }

      @Override
      public String toString() {
        return "Message::getUserProperty";
      }
    };

  static final Propagation.RemoteSetter<MessageConsumerRequest> SETTER =
    new Propagation.RemoteSetter<MessageConsumerRequest>() {
      @Override
      public Span.Kind spanKind() {
        return Span.Kind.CONSUMER;
      }

      @Override
      public void put(MessageConsumerRequest request, String name, String value) {
        request.delegate.putUserProperty(name, value);
      }

      @Override
      public String toString() {
        return "Message::putUserProperty";
      }
    };

  final MessageExt delegate;

  MessageConsumerRequest(MessageExt delegate) {
    if (delegate == null) {
      throw new NullPointerException("delegate == null");
    }
    this.delegate = delegate;
  }

  @Override
  public Span.Kind spanKind() {
    return Span.Kind.CONSUMER;
  }

  @Override
  public Object unwrap() {
    return delegate;
  }

  @Override
  public String operation() {
    return "receive";
  }

  @Override
  public String channelKind() {
    return "topic";
  }

  @Override
  public String channelName() {
    return delegate.getTopic();
  }

  @Override
  public String messageId() {
    return delegate.getMsgId();
  }
}
