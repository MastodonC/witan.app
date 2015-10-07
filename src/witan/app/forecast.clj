(ns witan.app.forecast
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [witan.app.config :as c]
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

(defn find-forecast-by-id
  [id]
  (hayt/select :forecast_headers (hayt/where {:id id})))

(defn find-forecast-by-name
  [name]
  (hayt/select :forecast_headers (hayt/where {:name name})))

(defn find-forecasts-by-series-id
  [series-id]
  (hayt/select :forecast_headers (hayt/where {:series_id series-id})))

(defn update-forecast-descendant-id
  [update-id descendant-id]
  (hayt/update :forecast_headers
               (hayt/set-columns {:descendant_id descendant-id})
               (hayt/where {:id update-id})))

(defn retrieve-forecast-most-recent-of-series
  [series-id]
  (->> series-id
       find-forecasts-by-series-id
       c/exec
       (sort-by :version >)
       first))

(defn create-forecast
  [{:keys [name description owner series_id version in_progress id]
    :or {series_id (uuid/random)
         version 0
         in_progress false
         id (uuid/random)}}]
  (hayt/insert :forecast_headers (hayt/values
                                  :id id
                                  :name  name
                                  :description description
                                  :created (tf/unparse (tf/formatters :date-time) (t/now))
                                  :owner owner ;; TODO check owner exists?
                                  :series_id    series_id
                                  :version version
                                  :in_progress in_progress
                                  :descendant_id nil)))

(defn add-forecast!
  [{:keys [name] :as forecast}]
  (let [existing-forecasts (c/exec (find-forecast-by-name name))
        id (uuid/random)]
    (when (empty? existing-forecasts)
      (c/exec (create-forecast (assoc forecast :id id)))
      (first (c/exec (find-forecast-by-id id))))))

(defn update-forecast!
  [{:keys [series-id owner inputs]}]
  (if-let [latest-forecast (retrieve-forecast-most-recent-of-series series-id)]
    (let [new-version (+ (:version latest-forecast) 1)
          new-id (uuid/random)
          new-forecast (assoc latest-forecast
                              :version new-version
                              :owner owner
                              :in_progress true
                              :id new-id)]
      (c/exec (create-forecast new-forecast))
      (c/exec (update-forecast-descendant-id (:id latest-forecast) new-id))
      (first (c/exec (find-forecast-by-id new-id))))))

(defn get-forecasts
  []
  (c/exec (hayt/select :forecast_headers)))

;;;;;;

(defresource forecasts
  :allowed-methods #{:get :post}
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (s/validate
                      [ws/Forecast]
                      (map ->Forecast (get-forecasts)))))
