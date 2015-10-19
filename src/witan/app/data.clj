(ns witan.app.data
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [witan.app.config :as c]))

(defn find-data-by-model-and-category
  [model-id category]
  (hayt/select :data_by_model_and_category (hayt/where {:model_id model-id
                                                        :category category})))

(defn find-data-by-data-id
  [data-id]
  (hayt/select :data_by_data_id (hayt/where {:data_id data-id})))

(defn create-data
  [{:keys [data-id category name publisher version s3-url model-id]} data-table]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert data-table (hayt/values :data_id data-id
                                         :category category
                                         :name name
                                         :model_id model-id
                                         :publisher publisher
                                         :version version
                                         :s3_url s3-url
                                         :created creation-time))))


(defn add-data!
  "TODO: for now manually upload and provide s3 url, but need to upload as part of this process"
  [{:keys [data-id category name model-id publisher version s3-url]
    :or {data-id (uuid/random)
         version 1}}]
  (mapv #(c/exec (create-data {:data-id data-id
                             :category category
                             :name name
                             :publisher publisher
                             :version version
                             :s3-url s3-url
                             :model-id model-id} %)) '(:data_by_data_id :data_by_model_and_category))
  (first (c/exec (find-data-by-data-id data-id))))
