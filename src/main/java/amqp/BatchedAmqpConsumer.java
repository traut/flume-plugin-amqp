/**
 * Licensed to Cloudera, Inc. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  Cloudera, Inc. licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package amqp;

import amqp.ImpatientBatcher.CantCreateBatchException;

import com.cloudera.flume.conf.FlumeConfiguration;
import com.cloudera.flume.core.Event;
import com.cloudera.flume.core.EventImpl;
import com.cloudera.util.Clock;
import com.cloudera.util.NetUtils;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Subclasses of {@link AmqpClient} that will bind to a queue and collect
 * messages from said queue and create {@link Event}s from them. These events are made available via the
 * blocking method {@link #getNextEvent(long, java.util.concurrent.TimeUnit)} ()}.
 * <p/>
 * The cancellation/shutdown policy for the consumer is to either interrupt the thread, or set the
 * {@link #running} flag to false.
 *
 * @see AmqpClient
 */
class BatchedAmqpConsumer extends AmqpConsumer {

	private static final Logger LOG = LoggerFactory.getLogger(BatchedAmqpConsumer.class);

	private ImpatientBatcher batcher;

	public BatchedAmqpConsumer(String host, int port, String virutalHost, String userName, String password,
			String exchangeName, String exchangeType, boolean durableExchange,
			String queueName, boolean durable, boolean exclusive, boolean autoDelete, String[] bindings,
			boolean useMessageTimestamp, String keystoreFile, String keystorePassword, String truststoreFile,
			String truststorePassword, String[] ciphers, int prefetchCount, int batchSize, long batchTimeoutMillis) {

		super(host, port, virutalHost, userName, password, exchangeName, exchangeType,
				durableExchange, queueName, durable, exclusive, autoDelete, bindings,
				useMessageTimestamp, keystoreFile, keystorePassword, truststoreFile, truststorePassword,
				ciphers, prefetchCount);

		this.batcher = new ImpatientBatcher(batchSize, batchTimeoutMillis);
	}


	/**
	 * Main run loop for consuming messages
	 */
	@Override
	protected void runConsumeLoop() {
		QueueingConsumer consumer = null;
		Thread currentThread = Thread.currentThread();

		while (isRunning() && !currentThread.isInterrupted()) {

			try {
				if (consumer == null) {
					consumer = configureChannelAndConsumer();
				}

				QueueingConsumer.Delivery delivery = consumer.nextDelivery(5000);
				if (delivery == null)
					continue;

				byte[] body = delivery.getBody();
				if (body != null) {
					Event event = createEventFromDelivery(delivery);
					LOG.debug("Message with tag " + delivery.getEnvelope().getDeliveryTag() + " received");
					batcher.addEvent(event, delivery.getEnvelope().getDeliveryTag());
				} else {
					LOG.warn("Received message with null body, ignoring message");
				}
				
				boolean ackBatch = false;

				Event megaEvent = null;
				try {
					megaEvent = batcher.getBatchAsEvent();
				} catch (CantCreateBatchException e) {
					LOG.error("Problem with the batch, streaming one event at a time");
					while(batcher.size() > 0) {
						this.events.add(batcher.getEventFromBatch());
					}
					ackBatch = true;
				}
				if (megaEvent != null) {
					this.events.add(megaEvent);
					ackBatch = true;
				}
				
				if (ackBatch) {
					for(long deliveryTag : batcher.pollAllTags()) {
						channel.basicAck(deliveryTag, false);
					}
				}

				LOG.debug("Ack for tag " + Long.toString(delivery.getEnvelope().getDeliveryTag()) + " sent out");
			} catch (InterruptedException e) {
				LOG.info("Consumer Thread was interrupted, shutting down...");
				setRunning(false);
			} catch (IOException e) {
				handleIOException(e);
			} catch (ShutdownSignalException e) {
				handleShutdownSignal(e);
			}
		}

		LOG.info("Exited runConsumeLoop with running={} and interrupt status={}", isRunning(), currentThread.isInterrupted());
	}

}
