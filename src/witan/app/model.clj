(ns witan.app.model
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [qbits.alia.codec :as codec]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [witan.app.config :as c]
            [witan.app.util :as util]
            [witan.app.schema :as ws]
            [witan.app.data :as data]
            [schema.core :as s])
  (:use [liberator.core :only [defresource]]))

(defn ->Model
  "Converts raw cassandra model into a ws/Model schema"
  [{:keys [version_id
           model_id
           created
           input_data
           input_data_defaults
           output_data] :as model}]
  (-> model
      (dissoc :version_id
              :model_id
              :created
              :input_data
              :input_data_defaults
              :output_data)
      (assoc :version-id version_id
             :model-id model_id
             :created (util/java-Date-to-ISO-Date-Time created)
             :input-data (mapv #(-> (hash-map :category %1)
                                    (cond-> (get input_data_defaults %1) (assoc :default (data/Data-> (get input_data_defaults %1))))) input_data)
             :output-data (mapv #(hash-map :category %1) output_data))))

(defn find-model-by-name
  [name]
  (hayt/select :model_names (hayt/where {:name name})))

(defn find-model-by-model-id
  [model-id]
  (hayt/select :models (hayt/where {:model_id model-id})))

(defn create-model-name
  [name model-id]
  (hayt/insert :model_names (hayt/values :name name :model_id model-id)))

(defn create-model
  [{:keys [name description owner model-id version version-id properties input-data output-data]
    :or {model-id (uuid/random)
         version 1
         version-id (uuid/random)}}]
  (let [input-defaults (filter :default input-data)]
    (hayt/insert :models (hayt/values
                          :version_id version-id
                          :name  name
                          :description description
                          :created (tf/unparse (tf/formatters :date-time) (t/now))
                          :owner owner ;; TODO check owner exists?
                          :model_id model-id
                          :version version
                          :properties (map (fn [p] (hayt/user-type p)) properties)
                          :input_data (mapv :category input-data)
                          :input_data_defaults (zipmap (map :category input-defaults)
                                                       (map (comp hayt/user-type :default) input-defaults))
                          :output_data (mapv :category output-data)))))

(defn update-default-input-data
  [model-id category data input-data-defaults]
  (let [new-input-map (assoc input-data-defaults category (hayt/user-type data))]
    (hayt/update :models
                 (hayt/set-columns {:input_data_defaults new-input-map})
                 (hayt/where {:model_id model-id}))))

(defn get-model-by-model-id
  [model-id]
  (first (c/exec (find-model-by-model-id model-id))))

(defn add-default-data-to-model!
  [model-id category data]
  (let [model (get-model-by-model-id model-id)
        input-data-map (:input_data_defaults model)]
    (when (some #{category} (:input_data model))
      (c/exec (update-default-input-data model-id category data input-data-map)))))


(defn add-model!
  [{:keys [name] :as model}]
  (when (empty? (c/exec (find-model-by-name name)))
    (let [model-id (uuid/random)]
      (c/exec (create-model (assoc model :model-id model-id)))
      (c/exec (create-model-name name model-id))
      (get-model-by-model-id model-id))))

(defn get-models
  []
  (c/exec (hayt/select :models)))

;;;;;;;

(defresource models
  :allowed-methods #{:get :post}
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (s/validate
                      [ws/Model]
                      (map ->Model (get-models)))))
