(ns witan.app.forecast
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [witan.app.config :refer [store-execute config]]
            [witan.schema :as ws]
            [schema.core :as s])
  (:use [liberator.core :only [defresource]]))

(defn java-Date-to-ISO-Date
  [datetime]
  (tf/unparse (tf/formatters :date-hour-minute-second)
              (tc/from-date datetime)))

(defn- ->Forecast
  "Converts raw cassandra forecast into a ws/Forecast schema"
  [{:keys [descendant_id
           in_progress
           series_id
           created] :as forecast}]
  (-> forecast
      (dissoc :descendant_id
              :in_progress
              :series_id
              :created)
      (assoc :descendant-id descendant_id
             :in-progress? in_progress
             :series-id series_id
             :created (java-Date-to-ISO-Date created))))

(defn find-forecast-by-name
  [name]
  (hayt/select :forecast_headers (hayt/where {:name name})))

(defn create-forecast
  [{:keys [name description owner]}]
  (hayt/insert :forecast_headers (hayt/values
                           :id    (uuid/random)
                           :name  name
                           :description description
                           :created (tf/unparse (tf/formatters :date-time) (t/now))
                           :owner owner ;; TODO check owner exists?
                           :series_id    (uuid/random)
                           :version 0
                           :in_progress false
                           :descendant_id nil)))

(defn add-forecast!
  [{:keys [name] :as forecast}]
  (let [exec (store-execute config)
        existing-forecasts (exec (find-forecast-by-name name))]
    (when (empty? existing-forecasts)
      (exec (create-forecast forecast)))))

(defn get-forecasts
  []
  (let [exec (store-execute config)]
    (exec (hayt/select :forecast_headers))))

;;;;;;

(defresource forecasts
  :allowed-methods #{:get :post}
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (s/validate
                      [ws/Forecast]
                      (map ->Forecast (get-forecasts)))))
