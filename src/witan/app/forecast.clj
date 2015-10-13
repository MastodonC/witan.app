(ns witan.app.forecast
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [witan.app.config :as c]
            [witan.app.schema :as ws]
            [witan.app.util :as util]
            [schema.core :as s]
            [clojure.tools.logging :as log])
  (:use [liberator.core :only [defresource]]))

(defn- ->ForecastHeaders
  "Converts raw cassandra forecast into a ws/Forecast schema"
  [{:keys [in_progress
           forecast_id
           created
           current_version_id] :as forecast}]
  (-> forecast
      (dissoc :in_progress
              :forecast_id
              :created
              :current_version_id)
      (assoc :in-progress? in_progress
             :forecast-id forecast_id
             :created (util/java-Date-to-ISO-Date-Time created)
             :version-id current_version_id)))

(defn- ->Forecast
  "Converts raw cassandra forecast into a ws/Forecast schema"
  [{:keys [in_progress
           series_id
           created
           version_id] :as forecast}]
  (let [cleaned (-> forecast
                    (dissoc :in_progress
                            :forecast_id
                            :created
                            :version_id)
                    (assoc :in-progress? in_progress
                           :forecast-id forecast_id
                           :created (util/java-Date-to-ISO-Date-Time created)
                           :version-id version_id))]
    (apply dissoc cleaned (for [[k v] cleaned :when (nil? v)] k))))

(defn find-forecast-by-id
  [id]
  (hayt/select :forecast_headers (hayt/where {:forecast_id id})))

(defn find-forecast-by-name-and-owner
  [name owner]
  (hayt/select :forecast_names (hayt/where {:name name
                                            :owner owner})))

(defn find-forecast-versions-by-id
  [id]
  (hayt/select :forecasts (hayt/where {:forecast_id id})))

(defn find-forecast-by-version-id
  [forecast-id version-id]
  (hayt/select :forecasts (hayt/where {:forecast_id forecast-id
                                       :version_id version-id})))

(defn update-forecast-current-version-id
  [forecast-id current-version-id new-version]
  (hayt/update :forecast_headers
               (hayt/set-columns {:current_version_id current-version-id
                                  :version new-version})
               (hayt/where {:forecast_id forecast-id})))

(defn retrieve-forecast-most-recent-of-series
  "uses the descending ordering of the versions in the forecast table"
  [id]
  (->> id
       find-forecast-by-id
       c/exec
       first))

(defn create-new-forecast
  [{:keys [name description owner forecast-id version-id]}]
  (hayt/insert :forecast_headers (hayt/values :name name
                                              :description description
                                              :owner owner
                                              :forecast_id forecast-id
                                              :current_version_id version-id
                                              :version 0)))


(defn create-forecast-name
  [{:keys [name owner]}]
  (hayt/insert :forecast_names (hayt/values :name name
                                            :owner owner)))

(defn create-forecast-version
  [{:keys [name description owner forecast-id version in_progress id version-id]}]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert :forecasts (hayt/values
                             :forecast_id forecast-id
                             :name  name
                             :description description
                             :created creation-time
                             :owner owner
                             :version_id version-id
                             :version version
                             :in_progress in_progress))))
(defn create-first-version
  [{:keys [forecast-id version-id name description owner]}]
  (create-forecast-version {:name name
                            :description description
                            :forecast-id forecast-id
                            :version-id version-id
                            :version 0
                            :owner owner
                            :in_progress false}))

(defn add-forecast!
  [{:keys [name owner] :as forecast}]
  (let [existing-forecasts (c/exec (find-forecast-by-name-and-owner name owner))
        id (uuid/random)
        version-id (uuid/random)
        new-forecast (assoc forecast :forecast-id id :version-id version-id)]
    (when (empty? existing-forecasts)
      (c/exec (create-new-forecast new-forecast))
      (c/exec (create-first-version new-forecast))
      (c/exec (create-forecast-name new-forecast))
      (first (c/exec (find-forecast-by-id id))))))

(defn update-forecast!
  [{:keys [forecast-id owner]}]
  (if-let [latest-forecast (retrieve-forecast-most-recent-of-series forecast-id)]
    (let [new-version (inc (:version latest-forecast))
          new-version-id (uuid/random)
          new-forecast (assoc latest-forecast
                              :version new-version
                              :version-id new-version-id
                              :owner owner
                              :in-progress true
                              :forecast-id forecast-id)]
      (c/exec (create-forecast-version new-forecast))
      (c/exec (update-forecast-current-version-id forecast-id new-version-id new-version))
      (c/exec (find-forecast-by-version-id forecast-id new-version-id)))))

(defn get-forecasts
  []
  (c/exec (hayt/select :forecast_headers)))

(defn get-forecast
  [id]
  (c/exec (hayt/select :forecasts (hayt/where {:forecast_id id}))))

;;;;;;

;;;;;; NOTES:
;; - We currently don't use `exists?` properly, but we should in the context of
;;   re-naming forecasts. Currently, duplicate owner+name combinations return
;;   304.

(defresource forecasts
  util/json-resource
  :allowed-methods #{:get :post}
  :available-media-types ["application/json"]
  :processable? (util/post!-processable-validation ws/NewForecast)
  :exists? (fn [ctx]
             (if (util/http-post? ctx)
               (let [{:keys [name]} (util/get-post-params ctx)
                     owner (util/get-user-id ctx)]
                 (not-empty (c/exec (find-forecast-by-name-and-owner name owner))))
               true))
  ;;
  :if-match-exists? false
  :if-unmodified-since-exists? false
  :if-none-match-exists? util/http-post?
  :if-none-match-star? true
  :if-none-match? true
  ;;
  :post! (fn [ctx]
           (let [forecast (util/get-post-params ctx)
                 owner (util/get-user-id ctx)]
             {::new-forecast (add-forecast! (assoc forecast :owner owner))}))
  :handle-created ::new-forecast
  :handle-ok  (fn [_] (s/validate
                       [ws/Forecast]
                       (map ->Forecast (get-forecasts)))))

(defresource forecast [id]
  :allowed-methods #{:get}
  :available-media-types ["application/json"]
  :handle-ok (fn [_] (s/validate
                      [ws/Forecast]
                      (map ->Forecast (get-forecast id)))))
