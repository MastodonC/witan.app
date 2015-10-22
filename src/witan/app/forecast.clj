(ns witan.app.forecast
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core     :as t]
            [clj-time.format   :as tf]
            [clj-time.coerce   :as tc]
            [witan.app.config :as c]
            [witan.app.schema :as ws]
            [witan.app.util :as util]
            [witan.app.model :as model]
            [witan.app.user :as user]
            [schema.core :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:use [liberator.core :only [defresource]]))

(defn- ->ForecastHeader
  "Converts raw cassandra forecast_header into a ws/Forecast schema"
  [{:keys [in_progress
           forecast_id
           created
           current_version_id
           model_id
           model_property_values
           owner_name] :as forecast}]
  (let [cleaned (-> forecast
                    (dissoc :in_progress
                            :forecast_id
                            :created
                            :current_version_id
                            :model_id
                            :model_property_values
                            :owner_name)
                    (assoc :in-progress? in_progress
                           :forecast-id forecast_id
                           :created (util/java-Date-to-ISO-Date-Time created)
                           :version-id current_version_id
                           :owner-name owner_name))]
    (apply dissoc cleaned (for [[k v] cleaned :when (nil? v)] k))))

(defn- ->Forecast
  "Converts raw cassandra forecast into a ws/Forecast schema"
  [{:keys [in_progress
           forecast_id
           created
           version_id
           owner_name
           model_id
           model_property_values] :as forecast}]
  (let [cleaned (-> forecast
                    (dissoc :in_progress
                            :forecast_id
                            :created
                            :version_id
                            :owner_name
                            :model_id
                            :model_property_values)
                    (assoc :in-progress? in_progress
                           :forecast-id forecast_id
                           :created (util/java-Date-to-ISO-Date-Time created)
                           :version-id version_id
                           :owner-name owner_name))]
    (apply dissoc cleaned (for [[k v] cleaned :when (nil? v)] k))))

(defn ->ForecastInfo
  "Converts raw cassandra forecast into a ws/ForecastInfo schema"
  [{:keys [in_progress
           forecast_id
           created
           version_id
           owner_name
           model_id
           model_property_values] :as forecast}]
  (let [cleaned (-> forecast
                    (dissoc :in_progress
                            :forecast_id
                            :created
                            :version_id
                            :owner_name
                            :model_id
                            :model_property_values)
                    (assoc :in-progress? in_progress
                           :forecast-id forecast_id
                           :created (util/java-Date-to-ISO-Date-Time created)
                           :version-id version_id
                           :owner-name owner_name
                           :model-id model_id
                           :property-values (vals model_property_values)
                           :inputs []
                           :outputs []))]
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

(defn find-forecast-by-version
  [forecast-id version]
  (hayt/select :forecasts (hayt/where {:forecast_id forecast-id
                                       :version version})))

(defn delete-forecast-by-version
  [forecast-id version]
  (hayt/delete :forecasts (hayt/where {:forecast_id forecast-id
                                       :version version})))

(defn update-forecast-current-version-id
  [forecast-id current-version-id new-version]
  (hayt/update :forecast_headers
               (hayt/set-columns {:current_version_id current-version-id
                                  :version new-version})
               (hayt/where {:forecast_id forecast-id})))

(defn get-forecast-by-version
  [forecast-id version]
  (first (c/exec (find-forecast-by-version forecast-id version))))

(defn get-most-recent-version
  [id]
  (first (c/exec (merge (find-forecast-versions-by-id id) (hayt/limit 1)))))

(defn create-new-forecast
  [{:keys [name description owner owner-name forecast-id version-id model-id model-property-values]}]
  (hayt/insert :forecast_headers (hayt/values :name name
                                              :description description
                                              :owner owner
                                              :owner_name owner-name
                                              :forecast_id forecast-id
                                              :current_version_id version-id
                                              :in_progress false
                                              :model_id model-id
                                              :model_property_values model-property-values
                                              :version 0)))


(defn create-forecast-name
  [{:keys [name owner]}]
  (hayt/insert :forecast_names (hayt/values :name name
                                            :owner owner)))

(defn create-forecast-version
  [{:keys [name description owner owner-name forecast-id version in_progress id version-id model-id model-property-values]}]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert :forecasts (hayt/values
                             :forecast_id forecast-id
                             :name  name
                             :description description
                             :created creation-time
                             :owner owner
                             :owner_name owner-name
                             :version_id version-id
                             :version version
                             :in_progress in_progress
                             :model_id model-id
                             :model_property_values model-property-values))))

(defn create-first-version
  [{:keys [forecast-id version-id name description owner owner-name model-id model-property-values]}]
  (create-forecast-version {:name name
                            :description description
                            :forecast-id forecast-id
                            :version-id version-id
                            :version 0
                            :owner owner
                            :owner-name owner-name
                            :in_progress false
                            :model-id model-id
                            :model-property-values model-property-values}))

(defn add-to-result-values
  [result name value]
  (update result :values assoc name (hayt/user-type {:name name :value value})))

(defn add-to-result-errors
  [result error]
  (update result :errors conj error))

(defn check-numeric-value
  [result property]
  (if (util/is-a-number? (:value property))
    (add-to-result-values result (:name property) (:value property))
    (add-to-result-errors result (str "Wrong type for " (:name property)))))

(defn add-text-value
  [result property]
  (add-to-result-values result (:name property) (:value property)))

(defn check-dropdown-value
  [result property type]
  (if (some #(= (:value property) %) (:enum_values type))
    (add-to-result-values result (:name property) (:value property))
    (add-to-result-errors result (str (:value property) " is not an accepted dropdown value"))))

(defn check-property-value
  [model-property-types result property]
  (let [corresponding-type (some (fn [type] (when (= (:name property) (:name type))
                                             type)) model-property-types)]
    (if corresponding-type
      (case (:type corresponding-type)
        "number" (check-numeric-value result property)
        "text" (add-text-value result property) ;; no validation needed
        "dropdown" (check-dropdown-value result property corresponding-type)
        (add-to-result-errors result (str "Unknown type " (:name property))))
      (add-to-result-errors result (str "Unknown property " (:name property))))))

(defn check-property-values
  "takes an array of maps with names and values
   finds corresponding model attributes
   validates values as having appropriate types
   transforms
      from query provided data [{:name x :value x}]
      into correct structure"
  [model-id property-values]
  (let [model (model/get-model-by-model-id model-id)
        model-properties (:properties model)]
    (reduce (partial check-property-value model-properties)
            {:errors [] :values {}}
            property-values)))

(defn add-forecast!
  [{:keys [name owner model-id model-properties] :as forecast}]
  (let [existing-forecasts (c/exec (find-forecast-by-name-and-owner name owner))
        id (uuid/random)
        version-id (uuid/random)
        uuid-model-id (util/to-uuid model-id)
        checked-property-values (check-property-values uuid-model-id model-properties)
        owner-name (-> owner user/retrieve-user :name)
        new-forecast (assoc forecast :forecast-id id
                            :version-id version-id
                            :model-id uuid-model-id
                            :model-property-values (:values checked-property-values)
                            :owner-name owner-name)]
    (when (and (empty? existing-forecasts) (empty? (:errors checked-property-values)))
      (c/exec (create-new-forecast new-forecast))
      (c/exec (create-first-version new-forecast))
      (c/exec (create-forecast-name new-forecast))
      (first (c/exec (find-forecast-by-id id))))))

(defn update-forecast!
  [{:keys [forecast-id owner]}]
  (if-let [latest-forecast (get-most-recent-version forecast-id)]
    (let [old-version (:version latest-forecast)
          new-version (inc old-version)
          new-version-id (uuid/random)
          owner-name (-> owner user/retrieve-user :name)
          new-forecast (assoc latest-forecast
                              :version new-version
                              :version-id new-version-id
                              :owner owner
                              :owner-name owner-name
                              :in-progress true
                              :forecast-id forecast-id
                              :model-id (:model_id latest-forecast)
                              :model-property-values (into {} (for [[k v] (:model_property_values latest-forecast)] [k (hayt/user-type v)])))]
      (c/exec (create-forecast-version new-forecast))
      (c/exec (update-forecast-current-version-id forecast-id new-version-id new-version))
      (when (= 0 old-version)
        (c/exec (delete-forecast-by-version forecast-id 0)))
      (c/exec (find-forecast-by-version forecast-id new-version)))))

(defn get-forecasts
  []
  (c/exec (hayt/select :forecast_headers)))


(defn get-forecast
  [{:keys [id version latest-version?]}]
  (cond
    version         (if-let [forecast (get-forecast-by-version id version)]
                      [forecast]
                      [])
    latest-version? (if-let [forecast (get-most-recent-version id)]
                      [forecast]
                      [])
    :else           (c/exec (find-forecast-versions-by-id id))))

;;;;;;

;;;;;; NOTES:
;; - We currently don't use `exists?` properly, but we should in the context of
;;   re-naming forecasts. Currently, duplicate owner+name combinations return
;;   304.

(defresource forecasts
  util/json-resource
  :allowed-methods #{:get :post}
  :processable? (fn [ctx]
                  (and ((util/post!-processable-validation ws/NewForecast) ctx)
                       (if (util/http-post? ctx)
                         (let [{:keys [model-id model-properties]} (util/get-post-params ctx)
                               uuid-model-id      (util/to-uuid model-id)
                               checked-properties (check-property-values uuid-model-id model-properties)]
                           [(empty? (:errors checked-properties)) {:property-errors (:errors checked-properties)}])
                         true)))
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
             {::new-forecast (->ForecastHeader (add-forecast! (assoc forecast :owner owner)))}))
  :handle-created ::new-forecast
  :handle-unprocessable-entity (fn [ctx] (if (not-empty (:property-errors ctx))
                                          {:error (str "Property errors: " (string/join ", " (:property-errors ctx)))}
                                          {:error "Validation error in given forecast."}))
  :handle-ok  (fn [_] (s/validate
                       [ws/Forecast]
                       (map ->ForecastHeader (get-forecasts)))))

(defresource forecast [{:keys [version latest-version?] :as args}]
  util/json-resource
  :allowed-methods #{:get}
  :exists? (fn [ctx]
             (let [result (get-forecast args)]
               (if (not-empty result)
                 (assoc ctx :result result))))
  :handle-ok (fn [{:keys [result] :as ctx}]
               (if (or version latest-version?)
                 ;; single
                 (s/validate
                  ws/ForecastInfo
                  (->ForecastInfo (first result)))
                 ;; multiple
                 (s/validate
                  [ws/Forecast]
                  (map ->Forecast result)))))
