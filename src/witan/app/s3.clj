(ns witan.app.s3
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [ring.util.codec :as codec]
            [clojure.tools.logging :as log]))

(def bucket
  (-> c/config :s3 :bucket))

(defn get-new-s3-key
  []
  (java.util.UUID/randomUUID))

(defn presigned-upload-url
  [name]
  (amazonica/generate-presigned-url {:endpoint "eu-central-1"} :bucket-name bucket :key name :expiration (-> 30 t/minutes t/from-now) :method "PUT"))

(defn presigned-download-url
  [name file-name]
  (let [header-overrides (com.amazonaws.services.s3.model.ResponseHeaderOverrides.)
        _ (when file-name (.setContentDisposition header-overrides (str "attachment; filename=" file-name)))]
    (amazonica/generate-presigned-url {:endpoint "eu-central-1"} :bucket-name bucket :key name :expiration (-> 30 t/minutes t/from-now) :method "GET" :response-headers header-overrides)))

(defn exists?
  [key]
  (try
    (boolean (amazonica/get-object-metadata bucket key))
    (catch com.amazonaws.services.s3.model.AmazonS3Exception _ false)))

(defn upload
  [key file]
  (amazonica/put-object {:endpoint "eu-central-1"}
                        :bucket-name bucket
                        :key key
                        :metadata {:server-side-encryption "AES256"}
                        :file file))
