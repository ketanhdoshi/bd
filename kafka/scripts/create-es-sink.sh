#!/usr/bin/env bash

# ---- Sink to Elasticsearch using timestamp-based index
#
# To use this, the source topic needs to already be in lowercase
#
# In KSQL you can do this with WITH (KAFKA_TOPIC='my-lowercase-topic') 
# when creating a stream or table
#
curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
  "name": "es_sink_ratings-with-customer-data-tsrouter",
  "config": {
    "topics": "ratings-enriched",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "key.ignore": "true",
    "schema.ignore": "true",
    "type.name": "type.name=kafkaconnect",
    "connection.url": "http://elasticsearch:9200",
    "transforms": "ExtractTimestamp,routeTS",
    "transforms.ExtractTimestamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",
    "transforms.ExtractTimestamp.timestamp.field" : "EXTRACT_TS",
    "transforms.routeTS.type":"org.apache.kafka.connect.transforms.TimestampRouter",  
    "transforms.routeTS.topic.format":"kafka-${topic}-${timestamp}",  
    "transforms.routeTS.timestamp.format":"YYYY-MM"
  }
}'

# ---- Sink to Elasticsearch with uppercase topic
#
# Use topic.index.map to map uppercase topic to lower case index name
#
# Note that this is not currently compatible with TimestampRouter
#
curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
  "name": "es_sink_unhappy_platinum_customers",
  "config": {
    "topics": "UNHAPPY_PLATINUM_CUSTOMERS",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
	"value.converter": "org.apache.kafka.connect.json.JsonConverter",
	"value.converter.schemas.enable": false,
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "key.ignore": "true",
    "schema.ignore": "true",
    "type.name": "type.name=kafkaconnect",
    "topic.index.map": "UNHAPPY_PLATINUM_CUSTOMERS:unhappy_platinum_customers",
    "connection.url": "http://elasticsearch:9200",
    "transforms": "ExtractTimestamp",
    "transforms.ExtractTimestamp.type": "org.apache.kafka.connect.transforms.InsertField$Value",
    "transforms.ExtractTimestamp.timestamp.field" : "EXTRACT_TS"
  }
}'

curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
 "name": "demo-es-sink",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "tasks.max": "1",
    "topics": "demo-es",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false",
    "key.ignore": "true",
    "schema.ignore": "true",
    "connection.url": "http://elasticsearch:9200",
    "type.name": "demoestype"
  }
}'

curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
 "name": "demo-mqtt-source",
  "config": {
    "connector.class" : "io.confluent.connect.mqtt.MqttSourceConnector",
    "tasks.max" : "1",
    "mqtt.server.uri" : "tcp://mosquitto:1883",
    "mqtt.topics" : "mqtt-iot",
    "kafka.topic" : "demo-iot",
    "confluent.topic.bootstrap.servers": "kafka:29092",
    "confluent.topic.replication.factor": "1",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false"
  }
}'

curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
 "name": "test-file-source",
  "config": {
    "connector.class": "FileStreamSource",
    "tasks.max": "1",
    "file": "/scripts/fileConnectSource.txt",
    "topic": "test-file"
  }
}'

curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
 "name": "test-file-sink",
  "config": {
    "connector.class": "FileStreamSink",
    "tasks.max": "1",
    "file": "/scripts/fileConnectSink.txt",
    "topics": "test-file"
  }
}'

curl -X "POST" "http://kafka-connect:18083/connectors/" \
     -H "Content-Type: application/json" \
     -d '{
 "name": "test-file-source-string",
  "config": {
    "connector.class": "FileStreamSource",
    "tasks.max": "1",
    "file": "/scripts/fileConnectString.txt",
    "topic": "test-file-string",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter",
    "key.converter.schemas.enable": "false",
    "value.converter.schemas.enable": "false"
  }
}'








