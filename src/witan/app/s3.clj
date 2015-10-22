(ns witan.app.s3
  (:require [s3-beam.handler :as s3b]
            [witan.app.config :as c]
            [environ.core :as environ]))

(def bucket
  (-> c/config :s3 :bucket))

(def aws-zone "eu-central-1")

(def aws-access-key
  (environ/env :aws-access-key))

(def aws-secret-key
  (environ/env :aws-secret-key))

(defn sign
  []
  (s3b/s3-sign bucket
               aws-zone
               aws-access-key
               aws-secret-key))
