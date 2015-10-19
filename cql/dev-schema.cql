// --------------------------------------------------------------------------------
// kill existing keyspace and recreate
DROP KEYSPACE witan;
CREATE KEYSPACE witan WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE witan.forecast_names (
    name text,
    owner uuid,
    PRIMARY KEY (name, owner)
) WITH CLUSTERING ORDER BY (owner ASC)
    AND bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'min_threshold': '4', 'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99.0PERCENTILE';

CREATE TYPE witan.model_property_value (
    name text,
    value text
);

CREATE TABLE witan.forecast_headers (
    forecast_id uuid PRIMARY KEY,
    created timestamp,
    current_version_id uuid,
    version int,
    description text,
    in_progress boolean,
    name text,
    owner uuid,
    model_id uuid,
    model_property_values map<text,frozen<model_property_value>>
);

CREATE TABLE witan.users (
    id uuid PRIMARY KEY,
    first_name text,
    name text,
    password_hash text,
    username text
) WITH bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'min_threshold': '4', 'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99.0PERCENTILE';
CREATE INDEX user_username ON witan.users (username);


CREATE TABLE witan.forecasts (
    forecast_id uuid,
    version int,
    created timestamp,
    description text,
    in_progress boolean,
    name text,
    owner uuid,
    version_id uuid,
    PRIMARY KEY (forecast_id, version)
) WITH CLUSTERING ORDER BY (version DESC)
    AND bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'min_threshold': '4', 'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99.0PERCENTILE';

// --------------------------------------------------------------------------------
// model properties
CREATE TYPE witan.model_property (
      name text,
      type text,
      context text,
      enum_values list<text>
);

// model names
CREATE TABLE witan.model_names (
    name text,
    model_id uuid,
    PRIMARY KEY (name, model_id)
) WITH CLUSTERING ORDER BY (model_id ASC)
    AND bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'min_threshold': '4', 'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99.0PERCENTILE';

// model headers
CREATE TABLE witan.data (
  data_id uuid,
  name text,
  model_id uuid,
  publisher uuid,
  version int,
  s3_url text,
  created timestamp,
  PRIMARY KEY (model_id, name)
);

CREATE TYPE witan.model_data (
  data_id uuid,
  name text,
  publisher uuid,
  version int,
  s3_url text,
  created timestamp
);

CREATE TABLE witan.models (
    name text,
    version_id uuid,
    description text,
    created timestamp,
    owner uuid,
    model_id uuid PRIMARY KEY,
    version int,
    properties list<frozen<witan.model_property>>,
    input_data map<text,frozen<model_data>>,
    output_data map<text, frozen<model_data>>
) WITH bloom_filter_fp_chance = 0.01
    AND caching = '{"keys":"ALL", "rows_per_partition":"NONE"}'
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy'}
    AND compression = {'sstable_compression': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99.0PERCENTILE';

// Data items
