to create or modify cassandra tables on mesos:
- ssh into one of the master boxes core@master.mesos
- run the script as folllows:
docker run -i -t --net=host --entrypoint=/usr/bin/cqlsh spotify/cassandra -e "script here" cassandra-dcos-node.cassandra.dcos.mesos 9160

The idea is to have an up-to-date version of the schema saved here at all times. This can be done by running following script when the schema is changed:
use myschema;\nDESCRIBE KEYSPACE;\n
and saving the output to the relevant file.
