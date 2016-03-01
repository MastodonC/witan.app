#!/usr/bin/env bash

SNAPSHOT=$1

if [ -z $SNAPSHOT ]; then
    echo "Please specify the snapshot reference as an argument"
    exit 1
fi

echo "downloading snapshot"


# copy from witan-cassandra-backup/witan/table_name_<some string>/snapshots/$SNAPSHOT/*
#      to   data_directory_location/keyspace_name/table_name/*

table_paths=$(aws s3 ls --profile witan s3://witan-cassandra-backup/ip-10-101-0-27.eu-central-1.compute.internal/witan/ | sed 's/^.*PRE //')
for path in ${table_paths[@]}; do
    table_name=${path%-*}
    echo "Downloading data for $table_name ..."
    mkdir -p "witan/$table_name"
    echo "aws s3 cp --profile witan --recursive s3://witan-cassandra-backup/ip-10-101-0-27.eu-central-1.compute.internal/witan/${path}snapshots/$SNAPSHOT witan/$table_name/"
    aws s3 cp --profile witan --recursive "s3://witan-cassandra-backup/ip-10-101-0-27.eu-central-1.compute.internal/witan/${path}snapshots/$SNAPSHOT" "witan/$table_name/"
done
echo "backup downloaded"
echo "now stop cassandra, copy directory under storagedir for your cassandra, start cassandra again, run nodetool repair"
exit 0
