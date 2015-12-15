(ns witan.app.db-setup
  (:require [clojure.java.io :as io]
            [qbits.alia :as alia]
            [witan.app.config :as c]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn cluster
  [host]
  (alia/cluster {:contact-points [host]}))

(defn session-setup
  [host]
  (alia/connect (cluster host)))

(defn replication-strategy
  [replication]
  (str "{'class': 'SimpleStrategy', 'replication_factor': '" replication "'}"))


(defn load-db-schema!
  "drops and recreates the existing schema with config specified keyspace and c* host.  WARNING: drops the existing keyspace."
  [config]
  (let [session (session-setup (get-in config [:cassandra-session :host]))
        keyspace (get-in config [:cassandra-session :keyspace])
        db-scripts (clojure.string/split (slurp (io/file (io/resource "db-schema.cql"))) #";")
        replication (get-in config [:cassandra-session :replication])]
    (log/warn "Dropping keyspace " keyspace)
    (try
      (alia/execute session (str "DROP KEYSPACE " keyspace ";"))
      (catch Exception e (str "keyspace could not be dropped " (.getMessage e))))
    (log/warn "Recreating keyspace " keyspace)
    (alia/execute session (str "CREATE KEYSPACE " keyspace " WITH replication = " (replication-strategy replication) " AND durable_writes = true;"))
    (log/warn "Loading schema." )
    (alia/execute session (str "USE " keyspace ";"))
    (run! (fn [scr] (log/debug scr) (when-not (clojure.string/blank? scr) (alia/execute session (str scr ";")))) db-scripts)
    (log/warn "Schema loaded.")))
