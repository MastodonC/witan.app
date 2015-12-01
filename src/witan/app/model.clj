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
            [witan.app.validation :as validation]
            [schema.core :as s]
            [clojure.tools.logging :as log]
            [hitman.core :as hitman]
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
           fixed_input_data] :as model}]
  (-> model
      (dissoc :version_id
              :model_id
              :created
              :input_data
              :input_data_defaults
              :output_data
              :fixed_input_data)
      (assoc :version-id version_id
             :model-id model_id
             :description (hitman/markdown description)
             :created (util/java-Date-to-ISO-Date-Time created)
             :input-data (mapv #(cond-> % (get input_data_defaults (:category %1))
                                        (assoc :default (data/->Data (get input_data_defaults (:category %1))))) input_data)
             :output-data (mapv #(hash-map :category %1) output_data)
             :fixed-input-data (mapv (fn [[category data]] (hash-map :category category :data (data/->Data data))) fixed_input_data))))

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
  [{:keys [name description owner model-id version version-id properties input-data output-data fixed-input-data]
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
                          :input_data (map hayt/user-type input-data)
                          :input_data_defaults (zipmap (map :category input-defaults)
                                                       (map (comp hayt/user-type data/data-to-db :default) input-defaults))
                          :output_data (mapv :category output-data)
                          :fixed_input_data  (zipmap (map :category fixed-input-data)
                                                     (map (comp hayt/user-type data/data-to-db :data) fixed-input-data))))))

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

(defresource validation [{:keys [model-id category file]}]
  :allowed-methods #{:post}
  :available-media-types ["application/json"]
  :processable? (fn [ctx]
                  (and
                   (validation/csv-extension? (:filename file))
                   (is-an-input-category? model-id category)))
  :handle-unprocessable-entity (fn [ctx] {:error (str "Could not get validation data for " model-id " - " category)})
  :post! (fn [ctx]
)
  :handle-created (fn [ctx]
               (log/info "you are here" (:tempfile file))
               {:boo "yeah"}))
