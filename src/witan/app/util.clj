(ns witan.app.util
  (:require [clojure.data.json :as json]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [schema.core :as s]))

(defn load-extensions!
  "Adds any extensions to types that we need"
  []
  ;; java.util.UUID
  (extend java.util.UUID
    clojure.data.json/JSONWriter
    {:-write (fn [uuid out] (.print out (json/write-str (str uuid))))}))

(defn java-Date-to-ISO-Date-Time
  "Converts a java.util.Date to an schema-contrib/ISO-Date-Time"
  [datetime]
  (tf/unparse (tf/formatters :date-hour-minute-second)
              (tc/from-date datetime)))

(defn is-json?
  "Is this a json string"
  [string]
  (try
    (do (json/read-str str) true)
    (catch Exception e false)))

(defn post!-processable-validation
  "In the context of a POST, checks the body params against a schema"
  [schema]
  (fn [ctx]
    (if (= :post (-> ctx :request :request-method))
      (let [params (-> ctx :request :body-params)]
        (nil? (s/check
               schema
               params)))
      true)))

;;;;;;;;;;;;;;;;

(def json-resource
  {:available-media-types ["application/json"]
   :known-content-type? (fn [ctx]
                          (if (= :post (-> ctx :request :request-method))
                            (= "application/json" (-> ctx :request :content-type)))
                          true)
   :handle-unprocessable-entity {:error "The schema of the required entity was incorrect."}
   :handle-malformed {:error "The request body was not valid JSON."}
   :malformed? (fn [ctx] (if (= :post (-> ctx :request :request-method))
                           (let [params (-> ctx :request :body-params)]
                             (if-not (instance? clojure.lang.PersistentArrayMap params)
                               {:representation {:media-type "application/json"}}))))})
