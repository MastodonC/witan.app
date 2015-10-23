(ns witan.app.s3
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [clj-time.core :as t]))

(def bucket
  (-> c/config :s3 :bucket))

(defn sign
  [name]
  {:presigned-url (amazonica/generate-presigned-url bucket name (-> 6 t/hours t/from-now) "PUT")})
