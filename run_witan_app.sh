#!/usr/bin/env bash

# config
cat <<EOF  > /root/.witan-app.edn
{:cassandra-session {:host "cassandra-dcos-node.cassandra.dcos.mesos"
                     :keyspace "witan"
                     :replication 3}
 :s3 {:bucket "witan-${ENVIRONMENT?not defined}-data"}}
EOF

# running jar
java -jar /root/witan-app.jar
