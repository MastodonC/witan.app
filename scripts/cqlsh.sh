#!/usr/bin/env bash
# https://hub.docker.com/_/cassandra/
docker run -it --link some-cassandra:cassandra --rm cassandra:2.2.4 cqlsh cassandra
