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
            [schema.core :as s])
  (:use [liberator.core :only [defresource]]))

(defn ->Model
  "Converts raw cassandra model into a ws/Model schema"
  [{:keys [version_id
           model_id
           created] :as model}]
  (-> model
      (dissoc :version_id
              :model_id
              :created)
      (assoc :version-id version_id
             :model-id model_id
             :created (util/java-Date-to-ISO-Date-Time created))))

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
  [{:keys [name description owner model-id version version-id properties]
    :or {model-id (uuid/random)
         version 1
         version-id (uuid/random)}}]
  (hayt/insert :models (hayt/values
                        :version_id version-id
                        :name  name
                        :description description
                        :created (tf/unparse (tf/formatters :date-time) (t/now))
                        :owner owner ;; TODO check owner exists?
                        :model_id model-id
                        :version version
                        :properties (map (fn [p] (hayt/user-type p)) properties))))

(defn get-model-by-model-id
  [model-id]
  (first (c/exec (find-model-by-model-id model-id))))

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
/
