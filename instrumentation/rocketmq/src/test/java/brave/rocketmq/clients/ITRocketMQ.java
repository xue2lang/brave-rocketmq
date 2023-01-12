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

import brave.messaging.MessagingRequest;
import brave.messaging.MessagingTracing;
import brave.sampler.Sampler;
import brave.sampler.SamplerFunction;
import brave.sampler.SamplerFunctions;
import brave.test.ITRemote;
import brave.test.IntegrationTestSpanHandler;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * @author JoeKerouac
 * @date 2023-01-11 10:17
 */
class ITRocketMQ extends ITRemote {

  static final DockerImageName IMAGE = DockerImageName.parse("apache/rocketmq:4.6.0");
  static final String TOPIC_PREFIX = "JoeKerouac_Test_";

  static final int NAMESERVER_PORT = 9876;
  static final int BROKER_PORT = 10911;

  static RocketMQContainer nameserver;
  static RocketMQContainer broker;

  @Rule public IntegrationTestSpanHandler producerSpanHandler = new IntegrationTestSpanHandler();
  @Rule public IntegrationTestSpanHandler consumerSpanHandler = new IntegrationTestSpanHandler();

  SamplerFunction<MessagingRequest> producerSampler = SamplerFunctions.deferDecision();
  SamplerFunction<MessagingRequest> consumerSampler = SamplerFunctions.deferDecision();

  RocketMQTracing producerTracing =
    RocketMQTracing.create(MessagingTracing
      .newBuilder(
        tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("producer").clearSpanHandlers()
          .addSpanHandler(producerSpanHandler).build())
      .producerSampler(r -> producerSampler.trySample(r)).build());

  RocketMQTracing consumerTracing =
    RocketMQTracing.create(MessagingTracing
      .newBuilder(
        tracingBuilder(Sampler.ALWAYS_SAMPLE).localServiceName("consumer").clearSpanHandlers()
          .addSpanHandler(consumerSpanHandler).build())
      .consumerSampler(r -> consumerSampler.trySample(r)).build());

  static final class RocketMQContainer extends GenericContainer<RocketMQContainer> {

    RocketMQContainer(String command, int... exportPorts) {
      super(IMAGE);
      if (exportPorts != null && exportPorts.length > 0) {
        List<String> portBindings = new ArrayList<>();
        for (int exportPort : exportPorts) {
          portBindings.add(String.format("%d:%d", exportPort, exportPort));
        }
        setPortBindings(portBindings);
      }

      // do not publish all ports
      withCreateContainerCmdModifier(cmd -> {
        if (cmd.getHostConfig() != null) {
          cmd.getHostConfig().withPublishAllPorts(false);
        }
      });

      setCommand(command);
      this.waitStrategy =
        Wait.forLogMessage(".*The.*boot success.*", 1).withStartupTimeout(Duration.ofSeconds(60));
    }
  }

  @BeforeClass
  public static void startRocketMQ() throws Exception {
    URL confUrl = ITRocketMQ.class.getClassLoader().getResource("broker.conf");

    nameserver = new RocketMQContainer("sh mqnamesrv", NAMESERVER_PORT, BROKER_PORT);
    nameserver.start();

    broker =
      new RocketMQContainer(String.format("sh mqbroker -n 127.0.0.1:%d -c /broker.conf",
        NAMESERVER_PORT));
    broker.addFileSystemBind(new File(confUrl.toURI()).getAbsolutePath(), "/broker.conf",
      BindMode.READ_ONLY);
    broker.setNetworkMode("container:" + nameserver.getContainerId());
    broker.start();
  }

  @AfterClass
  public static void stopRocketMQ() {
    broker.stop();
    nameserver.stop();
  }
}
