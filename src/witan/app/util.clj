(ns witan.app.util
  (:require [clojure.data.json :as json]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [clojure.tools.logging :as log]
            [schema.core :as s]))

(defn java-Date-to-ISO-Date-Time
  "Converts a java.util.Date to an schema-contrib/ISO-Date-Time"
  [datetime]
  (when datetime
    (tf/unparse (tf/formatters :date-hour-minute-second)
                (tc/from-date datetime))))

(defn http-post?
  [ctx]
  (= :post (-> ctx :request :request-method)))

(defn get-post-params
  [ctx]
  (-> ctx :request :body-params))

(defn get-user-id
  [ctx]
  (-> ctx :request :identity))

(defn post!-processable-validation
  "In the context of a POST, checks the body params against a schema"
  [schema]
  (fn [ctx]
    (if (http-post? ctx)
      (let [params (get-post-params ctx)
            result (s/check
                    schema
                    params)]
        (when result
          (log/error "Schema validation error:" result))
        (nil? result))
      true)))

(defn is-a-number? [txt]
  (try (Double/valueOf txt)
       (catch NumberFormatException _ false)))

(defmulti to-uuid type)
(defmethod to-uuid java.lang.String [id] (java.util.UUID/fromString id))
(defmethod to-uuid :default [id] id)

;;;;;;;;;;;;;;;;

(defn load-extensions!
  "Adds any extensions to types that we need"
  []
  ;; java.util.UUID
  (extend java.util.UUID
    clojure.data.json/JSONWriter
    {:-write (fn [uuid out] (.print out (json/write-str (str uuid))))})
  (extend java.util.Date
    clojure.data.json/JSONWriter
    {:-write (fn [date out] (.print out (json/write-str (str (java-Date-to-ISO-Date-Time date)))))}))

;;;;;;;;;;;;;;;;

;; Be aware of [this issue](https://github.com/clojure-liberator/liberator/issues/94)
;; when working on the 'malformed' parts

(def json-resource
  {:available-media-types ["application/json"]
   :known-content-type? (fn [ctx]
                          (if (http-post? ctx)
                            (= "application/json" (-> ctx :request :content-type)))
                          true)
   :handle-unprocessable-entity {:error "The schema of the required entity was incorrect."}
   :handle-malformed {:error "The request body was not valid JSON."}
   :handle-not-found {:error "The requested resource could not be found."}
   :malformed? (fn [ctx] (if (= :post (-> ctx :request :request-method))
                           (let [params (-> ctx :request :body-params)]
                             (if-not (instance? clojure.lang.PersistentArrayMap params)
                               {:representation {:media-type "application/json"}}))))})
