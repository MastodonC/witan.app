(ns witan.app.s3
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [ring.util.codec :as codec]
            [clojure.tools.logging :as log]))

(def bucket
  (-> c/config :s3 :bucket))

(defn presigned-url
  [name]
  (amazonica/generate-presigned-url {:endpoint "eu-central-1"} :bucket-name bucket :key name :expiration (-> 30 t/minutes t/from-now) :method "PUT"))

(defn presigned-download-url
  [name]
  (amazonica/generate-presigned-url {:endpoint "eu-central-1"} :bucket-name bucket :key name :expiration (-> 30 t/minutes t/from-now) :method "GET"))

(defn s3-beam-format
  [url name]
  (let [base-url (str (.getProtocol url) "://" (.getHost url) "/" name)
        url-keywords (clojure.walk/keywordize-keys (codec/form-decode (.getQuery url)))]
    (assoc url-keywords :Action base-url)))

(defn sign
  []
  (let [s3-key (str (java.util.UUID/randomUUID))]
    (log/info "returning pre-signed url for " s3-key)
    (s3-beam-format (presigned-url s3-key) s3-key)))

(defn exists?
  [key]
  (try
    (boolean (amazonica/get-object-metadata bucket key))
    (catch com.amazonaws.services.s3.model.AmazonS3Exception _ false)))
