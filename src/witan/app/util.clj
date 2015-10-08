(ns witan.app.util
  (:require [clojure.data.json :as json]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]))

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
