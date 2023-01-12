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

/**
 * @author JoeKerouac
 * @date 2023-01-10 15:19
 */
public class TraceConstants {

  public static final String TO_PREFIX = "To_";
  public static final String FROM_PREFIX = "From_";
  public static final String ROCKETMQ_SERVICE = "rocketmq";
  public static final String ROCKETMQ_TAGS = "rocketmq.tags";
  public static final String ROCKETMQ_TOPIC = "rocketmq.topic";
  public static final String ROCKETMQ_TRANSACTION_ID = "rocketmq.transactionId";
  public static final String ROCKETMQ_KEYS = "rocketmq.keys";
  public static final String ROCKETMQ_SOTRE_HOST = "rocketmq.store_host";
  public static final String ROCKETMQ_BODY_LENGTH = "rocketmq.body_length";
  public static final String ROCKETMQ_MSG_TYPE = "rocketmq.msg_type";
  public static final String ROCKETMQ_MSG_ID = "rocketmq.msg_id";
  public static final String ROCKETMQ_REGION_ID = "rocketmq.region_id";
  public static final String ROCKETMQ_RETRY_TIMERS = "rocketmq.retry_times";
  public static final String ROCKETMQ_SUCCESS = "rocketmq.success";
  public static final String ROCKETMQ_CONSUMER_GROUP = "rocketmq.consumerGroup";

}
