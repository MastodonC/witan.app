(ns witan.app.data
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [witan.app.config :as c]))

(defn create-data
  [{:keys [data-id category name publisher version s3-url model-id]}]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert :data (hayt/values :data_id data-id
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
  (c/exec (create-data {:data-id data-id
                        :category category
                        :name name
                        :publisher publisher
                        :version version
                        :s3-url s3-url
                        :model-id model-id})))
