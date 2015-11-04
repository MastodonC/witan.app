(ns witan.app.data
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [witan.app.config :as c]
            [witan.app.util :as util]
            [witan.app.s3 :as s3])
  (:use [liberator.core :only [defresource]]))

(defn Data->
  [{:keys [data_id
           file_name
           s3_key
           created] :as data}]
  (-> data
      (dissoc :data_id
              :file_name
              :s3_key
              :created)
      (assoc :data-id data_id
             :file-name file_name
             :s3-key s3_key
             :created (util/java-Date-to-ISO-Date-Time created))))

(defn find-data-by-category
  [category]
  (hayt/select :data_by_category (hayt/where {:category category})))

(defn find-data-by-data-id
  [data-id]
  (hayt/select :data_by_data_id (hayt/where {:data_id data-id})))

(defn find-data-name
  [name]
  (hayt/select :data_names (hayt/where {:name name})))

(defn find-data-by-s3-key
  [s3-key]
  (hayt/select :data_by_s3_key (hayt/where {:s3_key s3-key})))

(defn update-version-number-name
  [name version]
  (hayt/update :data_names (hayt/set-columns {:version version})
               (hayt/where {:name name})))

(defn get-current-version-name
  [name]
  (some-> (first (c/exec (find-data-name name)))
          :version))

(defn get-data-by-s3-key
  [s3-key]
  (-> s3-key
      (find-data-by-s3-key)
      (c/exec)
      (first)))

(defn get-data-by-category
  [category]
  (map #(assoc % :s3-url (.toString (s3/presigned-download-url (:s3_key %) (:file_name %)))) (c/exec (find-data-by-category category))))

(defn create-data
  [{:keys [data-id category name publisher version file-name s3-key]} data-table]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert data-table (hayt/values :data_id data-id
                                         :category category
                                         :name name
                                         :publisher publisher
                                         :version version
                                         :file_name file-name
                                         :s3_key s3-key
                                         :created creation-time))))

(defn add-data!
  "add data version"
  [{:keys [data-id category name file-name publisher s3-key]
    :or {data-id (uuid/random)}}]
  (let [version (or (get-current-version-name name) 1)]
    (run! #(c/exec (create-data {:data-id data-id
                                 :category category
                                 :name name
                                 :file-name file-name
                                 :publisher publisher
                                 :version version
                                 :s3-key s3-key} %)) '(:data_by_data_id :data_by_category :data_by_s3_key))
    (c/exec (update-version-number-name name version))
    (first (c/exec (find-data-by-data-id data-id)))))

(defresource data [{:keys [category]}]
  util/json-resource
  :allow-methods #{:get}
  :handle-ok (fn [ctx]
               (map Data-> (get-data-by-category category))))
