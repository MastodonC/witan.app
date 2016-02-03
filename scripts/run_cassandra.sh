#!/usr/bin/env bash
# https://hub.docker.com/_/cassandra/
docker rm some-cassandra
docker run --name some-cassandra -v /usr/local/cassandra:/var/lib/cassandra -d -p 9042:9042 cassandra:2.2.4
