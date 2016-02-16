#!/usr/bin/env bash
# https://hub.docker.com/r/ches/kafka/
DEFAULT_TOPIC=command
DEFAULT_ADDR=localhost

echo -n "Topic to write to [${DEFAULT_TOPIC}]: "
read name

TOPIC=${name:-$DEFAULT_TOPIC}

echo -n "Kafka Address [${DEFAULT_ADDR}]: "
read addr

ADDR=${addr:-$DEFAULT_ADDR}

docker run --net=host --rm --interactive ches/kafka kafka-console-producer.sh --topic ${TOPIC} --broker-list ${ADDR}:9092
