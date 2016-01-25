CREATE TABLE forecast_names (
    name text,
    owner uuid,
    PRIMARY KEY (name, owner)
);

CREATE TYPE model_property_value (
    name text,
    value text
);

CREATE TABLE forecast_headers (
    forecast_id uuid PRIMARY KEY,
    created timestamp,
    current_version_id uuid,
    version int,
    description text,
    in_progress boolean,
    error text,
    public boolean,
    name text,
    owner uuid,
    owner_name text,
    model_id uuid,
    model_property_values map<text,frozen<model_property_value>>
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    first_name text,
    name text,
    password_hash text,
    username text
);
CREATE INDEX user_username ON users (username);

CREATE TYPE data_item (
  data_id uuid,
  category text,
  name text,
  publisher uuid,
  publisher_name text,
  version int,
  file_name text,
  s3_key uuid,
  public boolean,
  created timestamp
);

CREATE TABLE forecasts (
    forecast_id uuid,
    version int,
    created timestamp,
    description text,
    in_progress boolean,
    error text,
    public boolean,
    name text,
    owner uuid,
    owner_name text,
    version_id uuid,
    latest boolean,
    model_id uuid,
    model_property_values map<text,frozen<model_property_value>>,
    inputs map<text,frozen<data_item>>,
    outputs map<text,frozen<list<data_item>>>,
    PRIMARY KEY (forecast_id, version)
) WITH CLUSTERING ORDER BY (version DESC);

// --------------------------------------------------------------------------------
// model properties
CREATE TYPE model_property (
      name text,
      display text,
      type text,
      context text,
      enum_values list<text>
);

CREATE TYPE model_category (
      category text,
      description text,
);

// model names
CREATE TABLE model_names (
    name text,
    model_id uuid,
    PRIMARY KEY (name, model_id)
);

CREATE TABLE data_by_data_id (
  data_id uuid PRIMARY KEY,
  category text,
  name text,
  publisher uuid,
  publisher_name text,
  version int,
  file_name text,
  s3_key uuid,
  public boolean,
  created timestamp
);

CREATE TABLE data_by_category (
  data_id uuid,
  category text,
  name text,
  publisher uuid,
  publisher_name text,
  version int,
  file_name text,
  s3_key uuid,
  public boolean,
  created timestamp,
  PRIMARY KEY (category, data_id)
);

CREATE TABLE data_by_s3_key (
  data_id uuid,
  category text,
  name text,
  publisher uuid,
  publisher_name text,
  version int,
  file_name text,
  s3_key uuid,
  public boolean,
  created timestamp,
  PRIMARY KEY (s3_key, data_id)
);

CREATE TABLE data_names (
  name text PRIMARY KEY,
  version int
);

CREATE TABLE models (
    name text,
    version_id uuid,
    description text,
    created timestamp,
    owner uuid,
    owner_name text,
    model_id uuid PRIMARY KEY,
    version int,
    properties list<frozen<model_property>>,
    input_data list<frozen<model_category>>,
    input_data_defaults map<text,frozen<data_item>>,
    output_data list<text>,
    fixed_input_data list<frozen<data_item>>
);

CREATE TABLE invite_tokens (
    username text,
    invite_token text,
    PRIMARY KEY (username, invite_token)
);
