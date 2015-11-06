(ns witan.app.model-execution
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [clojure-csv.core :as csv]
            [witan.models :as m]
            [clojure.tools.logging :as log]))

(comment "Striving to be as independent as possible from the other witan app namespaces,
since the plan is eventually to have this running on separate docker instances.
TODO: will need to get own config files")

(def bucket
  (-> c/config :s3 :bucket))

(defn download
  "Gets the content of an object
   NOTE ASSUMPTION: the input files are csv with \n or \r\n as eol characters"
  [key]
  (let [obj (amazonica/get-object :bucket-name bucket :key key)]
    (with-open
      [input-stream (:input-stream obj)]
      (csv/parse-csv (slurp input-stream)))))

(defn get-inputs
  "determine which s3 keys to download: given data or input defaults"
  [forecast model]
  (let [input-list (:input_data model)
        input-defaults (:input_data_defaults model)
        given-inputs (:inputs forecast)]
    (into {} (map (fn [category] (let  [given-data (get given-inputs category)
                                       default-data (get input-defaults category)]
                                  (cond
                                    given-data   [category (:s3-key given-data)]
                                    default-data [category (:s3-key default-data)]
                                    :else (throw (Exception. (str "Incomplete input data for model: " (:name model)))))
)) input-list))))

(defn download-data
  "download all inputs"
  [forecast model]
  (let [inputs (get-inputs forecast model)]
    (map (fn [[category s3-key]] [category (download s3-key)]))))

(defmulti execute-model (fn [_ model] [ (:name model) (:version model)]))
(defmethod execute-model ["Housing Linked Model" 1]
  [forecast model]
  (let [data (get-inputs forecast model)]
    (m/dclg-housing-linked-model data)))
(defmethod execute-model :default
  [_ _]
  (log/error "unknown model"))

(defn run [forecast model]
  )
