(ns witan.app.config
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [qbits.alia :as alia]))

(defn get-config
  "Gets info from a config file."
  [url]
  (edn/read-string (slurp url)))

(def ^:const config
  (let [home-config (io/file (System/getProperty "user.home")
                             ".witan-app.edn")
        default-config (io/resource "dev.witan-app.edn")]
    (try (get-config home-config)
         (catch java.io.FileNotFoundException e
           (get-config default-config)))))

(defn cluster [host]
  (alia/cluster {:contact-points [host]}))

(defn session [host keyspace]
  (alia/connect (cluster host) keyspace))

(defn store-execute [config]
  (let [cassandra-info (:cassandra-session config)
        session (session (:host cassandra-info) (:keyspace cassandra-info))]
    (partial alia/execute session)))

(defn exec []
  (store-execute config))
