#!/usr/bin/env bash
echo "WARNING: assumption that the actual schema definition only starts on line 6 of cql/dev-schema.cql"

echo "DROP KEYSPACE witan;" > cql/staging-schema.cql
echo "CREATE KEYSPACE witan WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '3'}  AND durable_writes = true;" >> cql/staging-schema.cql
sed -n '6,$p' cql/dev-schema.cql >> cql/staging-schema.cql
echo "Staging schema generated."
