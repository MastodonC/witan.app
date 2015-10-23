(ns witan.app.s3
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [clj-time.core :as t]
            [ring.util.codec :as codec]
            [s3-beam.handler :as s3-beam]
            [clojure.tools.logging :as log]))

(def bucket
  (-> c/config :s3 :bucket))

(defn presigned-url
  [name]
  (amazonica/generate-presigned-url bucket name (-> 6 t/hours t/from-now) "PUT"))

(defn s3-beam-format
  [url name]
  (let [base-url (str (.getProtocol url) "://" (.getHost url))
        url-keywords (clojure.walk/keywordize-keys (codec/form-decode (.getQuery url)))]
    (-> (clojure.set/rename-keys url-keywords {:Signature :signature})
        (assoc :acl "public-read"
               :success_action_status "201"
               :action base-url
               :key name
               :policy (s3-beam/policy bucket name 3600))
        (dissoc :Expires))))

(defn sign
  [name]
  (log/info "returning pre-signed url for " name)
  (s3-beam-format (presigned-url name) name))
