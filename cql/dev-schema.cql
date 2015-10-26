// --------------------------------------------------------------------------------
// kill existing keyspace and recreate
DROP KEYSPACE witan;
CREATE KEYSPACE witan WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'}  AND durable_writes = true;

CREATE TABLE witan.forecast_names (
    name text,
    owner uuid,
    PRIMARY KEY (name, owner)
);

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
    owner_name text,
    model_id uuid,
    model_property_values map<text,frozen<model_property_value>>
);

CREATE TABLE witan.users (
    id uuid PRIMARY KEY,
    first_name text,
    name text,
    password_hash text,
    username text
);
CREATE INDEX user_username ON witan.users (username);

CREATE TYPE witan.data_item (
  data_id uuid,
  category text,
  name text,
  publisher uuid,
  version int,
  file_name text,
  s3_key uuid,
  created timestamp
);

CREATE TABLE witan.forecasts (
    forecast_id uuid,
    version int,
    created timestamp,
    description text,
    in_progress boolean,
    name text,
    owner uuid,
    owner_name text,
    version_id uuid,
    model_id uuid,
    model_property_values map<text,frozen<model_property_value>>,
    inputs map<text,frozen<data_item>>,
    outputs map<text,frozen<data_item>>,
    PRIMARY KEY (forecast_id, version)
);

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
);

// model headers
CREATE TABLE witan.data_by_data_id (
  data_id uuid PRIMARY KEY,
  category text,
  name text,
  publisher uuid,
  version int,
  file_name text,
  s3_key uuid,
  created timestamp
);

CREATE TABLE witan.data_by_category (
  data_id uuid,
  category text PRIMARY KEY,
  name text,
  publisher uuid,
  version int,
  file_name text,
  s3_key uuid,
  created timestamp
);

CREATE TABLE witan.data_names (
  name text PRIMARY KEY,
  version int
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
    input_data list<text>,
    input_data_defaults map<text,frozen<data_item>>,
    output_data list<text>
);
