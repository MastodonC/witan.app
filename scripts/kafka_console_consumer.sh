#!/usr/bin/env bash
# https://hub.docker.com/r/ches/kafka/
DEFAULT_TOPIC=command
DEFAULT_ADDR=localhost

echo -n "Topic to read [${DEFAULT_TOPIC}]: "
read name

TOPIC=${name:-$DEFAULT_TOPIC}

echo -n "ZooKeeper Address [${DEFAULT_ADDR}]: "
read addr

ADDR=${addr:-$DEFAULT_ADDR}

docker run --net=host --rm ches/kafka kafka-console-consumer.sh --topic ${TOPIC} --from-beginning --zookeeper ${ADDR}:2181
