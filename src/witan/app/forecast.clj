(ns witan.app.forecast
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [witan.app.config :refer [store-execute config]]))

(defn find-forecast
  [name]
  (hayt/select :Forecasts (hayt/where {:name name})))

(defn create-forecast
  [{:keys [name description owner model]}]
  (hayt/insert :Forecasts (hayt/values
                           :id    (uuid/random)
                           :name  name
                           :description description
                           :version 0
                           :ninputs 3
                           :noutputs [2 3]
                           :owner (uuid/random)
                           :lastmodifier (uuid/random)
                           :lastmodified (tf/unparse (tf/formatters :date-time) (t/now))
                           :model (uuid/random)
                           :descendant (uuid/random))))

(defn add-forecast!
  [{:keys [name] :as forecast}]
  (let [exec (store-execute config)
        existing-forecasts (exec (find-forecast name))]
    (when (empty? existing-forecasts)
      (exec (create-forecast forecast)))))
