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
            [witan.app.user :as usr]
            [witan.validation :as validation]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [markdown.core :as markdown]
            [ring.util.mime-type :refer [ext-mime-type]])
  (:use [liberator.core :only [defresource]]))

(defn ->Model
  "Converts raw cassandra model into a ws/Model schema"
  [{:keys [version_id
           model_id
           created
           description
           input_data
           input_data_defaults
           output_data
           fixed_input_data
           owner_name] :as model}]
  (-> model
      (dissoc :version_id
              :model_id
              :created
              :input_data
              :input_data_defaults
              :output_data
              :fixed_input_data
              :owner_name)
      (assoc :version-id version_id
             :model-id model_id
             :owner-name owner_name
             :description (markdown/md-to-html-string description)
             :created (util/java-Date-to-ISO-Date-Time created)
             :input-data (->> input_data
                              (mapv #(assoc % :description (-> % :description markdown/md-to-html-string)))
                              (mapv #(cond-> % (get input_data_defaults (:category %1))
                                             (assoc :default (data/->Data (get input_data_defaults (:category %1)))))))
             :output-data (mapv #(hash-map :category %1) output_data)
             :fixed-input-data (mapv data/->Data fixed_input_data))))

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
  [{:keys [name description owner owner-name model-id version version-id properties input-data output-data fixed-input-data]
    :or {model-id (uuid/random)
         version 1
         version-id (uuid/random)}}]
  (let [input-defaults (filter :default input-data)]
    (hayt/insert :models (hayt/values
                          :version_id version-id
                          :name  name
                          :description description
                          :created (tf/unparse (tf/formatters :date-time) (t/now))
                          :owner owner
                          :owner_name owner-name
                          :model_id model-id
                          :version version
                          :properties (map (fn [p] (hayt/user-type p)) properties)
                          :input_data (map hayt/user-type input-data)
                          :input_data_defaults (zipmap (map :category input-defaults)
                                                       (map (comp hayt/user-type data/data-to-db :default) input-defaults))
                          :output_data (mapv :category output-data)
                          :fixed_input_data (mapv (comp hayt/user-type data/data-to-db) fixed-input-data)))))

(defn update-default-input-data
  [model-id category data input-data-defaults]
  (let [new-input-map (assoc input-data-defaults category (hayt/user-type data))]
    (hayt/update :models
                 (hayt/set-columns {:input_data_defaults new-input-map})
                 (hayt/where {:model_id model-id}))))

(defn get-model-by-model-id
  [model-id]
  (first (c/exec (find-model-by-model-id model-id))))

(defn is-an-input-category?
  [model-id category]
  (let [model (get-model-by-model-id model-id)]
    (some->> model
             :input_data
             (map :category)
             (some #{category}))))

(defn add-default-data-to-model!
  [model-id category data]
  (let [model (get-model-by-model-id model-id)
        input-data-map (:input_data_defaults model)]
    (when (some #(if (= (:category %) category) %) (:input_data model))
      (c/exec (update-default-input-data model-id category data input-data-map)))))


(defn add-model!
  [{:keys [name owner] :as model}]
  (when (empty? (c/exec (find-model-by-name name)))
    (let [model-id (uuid/random)
          user-name (:name (usr/retrieve-user owner))]
      (c/exec (create-model (assoc model
                                   :model-id model-id
                                   :owner-name user-name)))
      (c/exec (create-model-name name model-id))
      (get-model-by-model-id model-id))))

(defn get-models
  []
  (c/exec (hayt/select :models)))

;;;;;;;

(defresource models
  :allowed-methods #{:get :post}
  :available-media-types ["application/json"]
  :handle-ok (fn [_]
               (s/validate
                      [ws/Model]
                      (map ->Model (get-models)))))

(defresource model [{:keys [id]}]
  :allowed-methods #{:get}
  :exists? (fn [ctx]
             (let [result (get-model-by-model-id id)]
               [result {:result result}]))
  :available-media-types ["application/json"]
  :handle-ok (fn [{:keys [result]}]
               (s/validate
                     ws/Model
                     (->Model result))))
