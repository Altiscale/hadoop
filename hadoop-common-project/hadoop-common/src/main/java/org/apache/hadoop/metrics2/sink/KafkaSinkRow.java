/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
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

package org.apache.hadoop.metrics2.sink;

import kafka.javaapi.producer.Producer;
import kafka.producer.KeyedMessage;
import kafka.producer.ProducerConfig;
import org.apache.commons.configuration.SubsetConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.classification.InterfaceAudience;
import org.apache.hadoop.classification.InterfaceStability;
import org.apache.hadoop.metrics2.AbstractMetric;
import org.apache.hadoop.metrics2.MetricsException;
import org.apache.hadoop.metrics2.MetricsRecord;
import org.apache.hadoop.metrics2.MetricsSink;
import org.apache.hadoop.metrics2.MetricsTag;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Calendar;
import java.util.Properties;

/**
 * A metrics sink that writes to a Kafka broker
 */
@InterfaceAudience.Public
@InterfaceStability.Evolving
public class KafkaSinkRow implements MetricsSink, Closeable {
    private static final Log LOG = LogFactory.getLog(KafkaSinkRow.class);
    private static final String BROKER_LIST = "broker_list";
    private static final String TOPIC = "topic";

    private String brokerlist = null;
    private String topic = null;
    private Producer<Integer, byte[]> producer = null;

    @Override
    public void init(SubsetConfiguration conf) {
        Properties props = new Properties();
        brokerlist = conf.getString(BROKER_LIST);
        props.put("metadata.broker.list", brokerlist);
        LOG.info("Kafka brokerrs: " + brokerlist);

		// Get the topic name from configuartion.
        topic = conf.getString(TOPIC);
        LOG.info("Kafka topicss " + topic);
        if (topic.equals(""))
            throw new MetricsException("Kafka topic can not be null");

        props.put("serializer.class", "kafka.serializer.DefaultEncoder");
        props.put("request.required.acks", "0");

        ProducerConfig config = new ProducerConfig(props);
        try {
            // Create the producer object.
            producer = new Producer<Integer, byte[]>(config);
        } catch (Exception e) {
            throw new MetricsException("Error creating Producer, " + brokerlist, e);
        }
    }

    @Override
    public void putMetrics(MetricsRecord record) {
        StringBuilder jsonLines = new StringBuilder();
        StringBuilder metricsPathPrefix = new StringBuilder();

        // Configure the hierarchical place to display the graph.
        metricsPathPrefix.append(record.context()).append(".").append(record.name());

        for (MetricsTag tag : record.tags()) {
            if (tag.value() != null) {
                metricsPathPrefix.append(".");
                metricsPathPrefix.append(tag.name());
                metricsPathPrefix.append("=");
                metricsPathPrefix.append(tag.value().replaceAll("[\\p{Cc}]", ""));
            }
        }

        // Round the timestamp to second.
        String hostname = new String("null");
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) { 
            LOG.warn("Error getting Hostname, going to continue");
        }
        Long timestamp = record.timestamp();
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        String date = cal.get(Calendar.YEAR) + "-" + cal.get(Calendar.MONTH) + "-" + cal.get(Calendar.DAY_OF_MONTH);
        String time = cal.get(Calendar.HOUR_OF_DAY) + ":" + cal.get(Calendar.MINUTE) + ":" + cal.get(Calendar.SECOND);

        // Collect datapoints and create the avro object.

            jsonLines.append("{\"hostname\": \"" + hostname);
            jsonLines.append("\", \"timestamp\": " + timestamp);
            jsonLines.append(", \"date\": \"" + date);
            jsonLines.append("\",\"time\": \"" + time);
            jsonLines.append("\",\"name\": \"" + record.name() + "\" ");
            for (MetricsTag tag : record.tags()) {
                jsonLines.append(", \"" + tag.name().toString().replaceAll("[\\p{Cc}]", "") + "\": ");
                jsonLines.append(" \"" + tag.value().toString() + "\"");
            }
            for (AbstractMetric metric : record.metrics()) {
                jsonLines.append(", \"" + metric.name().toString().replaceAll("[\\p{Cc}]", "") + "\": ");
                jsonLines.append(" \"" + metric.value().toString() + "\"");
            }
            jsonLines.append("}");
            // jsonLines.append("\n");

            // Create the message.
            KeyedMessage<Integer, byte[]> data = new KeyedMessage<Integer, byte[]>(topic, jsonLines.toString().getBytes());

            try {
                if(producer != null){
                  producer.send(data);
                } else {
                  throw new MetricsException("Producer in KafkaSink is null!");
                }
            } catch (Exception e) {
                throw new MetricsException("Error sending metrics", e);
            }

            jsonLines.setLength(0);


    }

    @Override
    public void flush() {
        try {
            LOG.warn("Kafka seems not to have any flush() mechanism!");
        } catch (Exception e) {
            throw new MetricsException("Error flushing metrics", e);
        }
    }

    @Override
    public void close() throws IOException {
      try {
        producer.close();
        LOG.info("producer in KafkaSink is closed!");
      } catch (Throwable e){
        throw new MetricsException("Error closing producer", e);
      } finally {
        LOG.info("proudcer in KafkaSink is closed!");
      }
    }
}
