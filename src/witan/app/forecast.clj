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
            [witan.app.model-execution :as mex]
            [witan.app.user :as u]
            [witan.app.s3 :as s3]
            [witan.app.data :as data]
            [schema.core :as s]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.core.async :as a :refer [go]])
  (:use [liberator.core :only [defresource]]))

(defn- ->ForecastHeader
  "Converts raw cassandra forecast_header into a ws/Forecast schema"
  [{:keys [in_progress
           public
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
                            :owner_name
                            :public)
                    (assoc :in-progress? in_progress
                           :public? public
                           :latest? true
                           :forecast-id forecast_id
                           :created (util/java-Date-to-ISO-Date-Time created)
                           :version-id current_version_id
                           :owner-name owner_name))]
    (apply dissoc cleaned (for [[k v] cleaned :when (nil? v)] k))))

(defn- ->Forecast
  "Converts raw cassandra forecast into a ws/Forecast schema"
  [{:keys [in_progress
           public
           latest
           forecast_id
           created
           version_id
           owner_name
           model_id
           model_property_values] :as forecast}]
  (let [cleaned (-> forecast
                    (dissoc :in_progress
                            :public
                            :latest
                            :forecast_id
                            :created
                            :version_id
                            :owner_name
                            :model_id
                            :model_property_values
                            :inputs
                            :outputs)
                    (assoc :in-progress? in_progress
                           :public? public
                           :latest? latest
                           :forecast-id forecast_id
                           :created (util/java-Date-to-ISO-Date-Time created)
                           :version-id version_id
                           :owner-name owner_name))]
    (apply dissoc cleaned (for [[k v] cleaned :when (nil? v)] k))))

(defn ->ForecastInfo
  "Converts raw cassandra forecast into a ws/ForecastInfo schema"
  [{:keys [in_progress
           public
           latest
           forecast_id
           created
           version_id
           owner_name
           model_id
           model_property_values
           inputs
           outputs] :as forecast}]
  (let [divide-data-map (fn [inputs urls?] (map
                                            (fn [[k v]] (hash-map k (if (vector? v)
                                                                      (map #(data/->Data % urls?) v)
                                                                      (data/->Data v urls?)))) inputs))
        inputs  (divide-data-map inputs false)
        outputs (divide-data-map outputs true)
        cleaned         (-> forecast
                            (dissoc :in_progress
                                    :public
                                    :latest
                                    :forecast_id
                                    :created
                                    :version_id
                                    :owner_name
                                    :model_id
                                    :model_property_values)
                            (assoc :in-progress? in_progress
                                   :public? public
                                   :latest? latest
                                   :forecast-id forecast_id
                                   :created (util/java-Date-to-ISO-Date-Time created)
                                   :version-id version_id
                                   :owner-name owner_name
                                   :model-id model_id
                                   :property-values (vals model_property_values)
                                   :inputs inputs
                                   :outputs outputs))]
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

(defn find-most-recent-version
  [forecast-id]
  (merge (find-forecast-versions-by-id forecast-id) (hayt/limit 1)))

(defn find-forecast-by-version
  [forecast-id version]
  (hayt/select :forecasts (hayt/where {:forecast_id forecast-id
                                       :version version})))

(defn delete-forecast-by-version
  [forecast-id version]
  (hayt/delete :forecasts (hayt/where {:forecast_id forecast-id
                                       :version version})))

(defn update-forecast-latest
  [forecast-id version latest?]
  (hayt/update :forecasts
               (hayt/set-columns {:latest latest?})
               (hayt/where {:forecast_id forecast-id :version version})))

(defn update-forecast-current-version-id
  [{:keys [forecast-id version-id version in-progress?]}]
  (hayt/update :forecast_headers
               (hayt/set-columns {:current_version_id version-id
                                  :version version
                                  :in_progress in-progress?
                                  :created (tf/unparse (tf/formatters :date-time) (t/now))})
               (hayt/where {:forecast_id forecast-id})))

(defn update-forecast-outputs
  [{:keys [forecast-id version outputs]}]
  (hayt/update :forecasts
               (hayt/set-columns {:outputs outputs
                                  :in_progress false})
               (hayt/where {:forecast_id forecast-id :version version})))

(defn get-most-recent-version
  [id]
  (first (c/exec (find-most-recent-version id))))

(defn get-forecast-version
  [forecast-id version]
  (first (c/exec (find-forecast-by-version forecast-id version))))

(defn create-new-forecast
  [{:keys [name description owner owner-name forecast-id version-id model-id model-property-values public?]}]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert :forecast_headers (hayt/values :name name
                                                :description description
                                                :created creation-time
                                                :owner owner
                                                :owner_name owner-name
                                                :forecast_id forecast-id
                                                :current_version_id version-id
                                                :in_progress false
                                                :public public?
                                                :model_id model-id
                                                :model_property_values model-property-values
                                                :version 0))))


(defn create-forecast-name
  [{:keys [name owner]}]
  (hayt/insert :forecast_names (hayt/values :name name
                                            :owner owner)))

(defn create-forecast-version
  [{:keys [name description owner owner-name forecast-id version in-progress? public? id version-id model-id model-property-values inputs]}]
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
                             :in_progress in-progress?
                             :public public?
                             :latest true
                             :model_id model-id
                             :model_property_values model-property-values
                             :inputs inputs))))

(defn create-first-version
  [{:keys [forecast-id version-id name description owner owner-name model-id model-property-values public?]}]
  (create-forecast-version {:name name
                            :description description
                            :forecast-id forecast-id
                            :version-id version-id
                            :version 0
                            :owner owner
                            :owner-name owner-name
                            :in-progress? false
                            :public? public?
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

(defn conclude-forecast!
  [{:keys [forecast-id version] :as args}]
  (c/exec (update-forecast-outputs args))
  (let [forecast (->Forecast (first (c/exec (find-forecast-by-version forecast-id version))))]
    (c/exec (update-forecast-current-version-id forecast))
    forecast))

(defn process-output-data!
  [[category output] public?]
  (let [output-as-data (data/add-data! (assoc (first output) :public? public?))] ;; TODO we only process the first item. post MVP1 we may have >1
    (hash-map category [(hayt/user-type output-as-data)])))

(defn run-model!
  ([forecast]
   (run-model! forecast (model/get-model-by-model-id (:model_id forecast))))
  ([forecast model]
   (go ;; temporary solution
     (log/info "Starting to run model: " (:model_id forecast) (:name model) (str "v" (:version model)))
     (try
       (let [outputs   (mex/execute-model forecast model)
             data      (into {} (->> (vec outputs)
                                     (first)
                                     (map #(process-output-data! % (:public? forecast)))))]
         (log/info "Finished running model " (:model_id forecast) "-" (count data) "output(s) returned." outputs)
         (conclude-forecast! (assoc (->Forecast forecast) :outputs data)))
       (catch Exception e (log/error "Model" (:model_id forecast) "threw an error:" (.getMessage e) (clojure.stacktrace/print-stack-trace e)))))))

(defn add-forecast!
  [{:keys [name owner model-id model-properties public?]
    :or {public? false}
    :as forecast}]
  (let [existing-forecasts (c/exec (find-forecast-by-name-and-owner name owner))
        id (uuid/random)
        version-id (uuid/random)
        uuid-model-id (util/to-uuid model-id)
        checked-property-values (check-property-values uuid-model-id model-properties)
        owner-name (-> owner u/retrieve-user :name)
        new-forecast (assoc forecast :forecast-id id
                            :version-id version-id
                            :model-id uuid-model-id
                            :public? public?
                            :model-property-values (:values checked-property-values)
                            :owner-name owner-name)]
    (when (and (empty? existing-forecasts) (empty? (:errors checked-property-values)))
      (c/exec (create-new-forecast new-forecast))
      (c/exec (create-first-version new-forecast))
      (c/exec (create-forecast-name new-forecast))
      (first (c/exec (find-forecast-by-id id))))))

(defn has-all-inputs?
  [model inputs]
  (let [model-input-categories (->> (:input_data model)
                                    (map :category)
                                    (set))
        supplied-input-categories (-> inputs keys set)
        intersection-count (count (clojure.set/intersection model-input-categories supplied-input-categories))]
    (= intersection-count (count model-input-categories))))

(defn locate-input-by-data-id
  [[category {:keys [data-id]}]]
  (hash-map (name category) (data/get-data-by-data-id data-id)))

(defn update-forecast!
  [{:keys [forecast-id owner inputs]}]
  (if-let [latest-forecast (get-most-recent-version forecast-id)]
    (let [model (model/get-model-by-model-id (:model_id latest-forecast))]
      (if (has-all-inputs? model inputs)
        (let [old-version (:version latest-forecast)
              new-version (inc old-version)
              new-version-id (uuid/random)
              owner-name (-> owner u/retrieve-user :name) ;; TODO should check for nil
              new-forecast (assoc latest-forecast
                                  :version new-version
                                  :version-id new-version-id
                                  :owner owner
                                  :owner-name owner-name
                                  :in-progress? true
                                  :public? (:public latest-forecast)
                                  :forecast-id forecast-id
                                  :model-id (:model_id latest-forecast)
                                  :model-property-values (into {} (for [[k v] (:model_property_values latest-forecast)] [k (hayt/user-type v)]))
                                  :inputs (into {} (for [[k v] inputs] [(name k) (hayt/user-type v)])))]
          (c/exec (create-forecast-version new-forecast))
          (c/exec (update-forecast-current-version-id new-forecast))
          (if (zero? old-version)
            (c/exec (delete-forecast-by-version forecast-id 0))
            (c/exec (update-forecast-latest forecast-id old-version false)))
          (run-model! (assoc new-forecast :inputs inputs) model) ;; assoc to use the original inputs (not UDT'd)
          (first (c/exec (find-forecast-by-version forecast-id new-version))))
        (log/error "The incorrect number of inputs was supplied.")))))

(defn get-forecasts
  [user]
  (filter #(or (:public %)
               (= (:owner %) user))
          (c/exec (hayt/select :forecast_headers))))


(defn get-forecast
  [{:keys [id version latest-version?]}]
  (c/exec
   (cond
     version         (find-forecast-by-version id version)
     latest-version? (find-most-recent-version id)
     :else           (find-forecast-versions-by-id id))))

(defn all-categories-exist-in-model?
  [forecast categories]
  (some->> forecast
           :model_id
           (model/get-model-by-model-id)
           :input_data
           (fn [model-categories] (every? #(some #{(name %)} model-categories) categories))))

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
  :handle-ok  (fn [ctx]
                (s/validate
                 [ws/Forecast]
                 (map ->ForecastHeader (get-forecasts (util/get-user-id ctx))))))

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

(defresource version [{:keys [id user-id]}]
  util/json-resource
  :allowed-methods #{:post}
  :processable? (fn [ctx]
                  (let [forecast (get-most-recent-version id)
                        inputs   (:inputs (util/get-post-params ctx))
                        tests    [[(fn [] forecast)                                                    "forecast is nil"]
                                  [(fn [] ((util/post!-processable-validation ws/UpdateForecast) ctx)) "failed schema validation"]]
                        result (reduce (fn [a x] (if-not ((first x)) (reduced (second x)) nil)) [] tests)]
                    (when result
                      (log/error "The updated forecast was unable to be processed (422):" result))
                    (nil? result))) ;; return a bool, true if result is nil
  :handle-created (fn [ctx]
                    (let [given-inputs (:inputs (util/get-post-params ctx))
                          inputs (into {} (map locate-input-by-data-id given-inputs))
                          new-forecast (update-forecast! {:forecast-id id
                                                          :owner user-id
                                                          :inputs inputs})]
                      (s/validate ws/ForecastInfo (->ForecastInfo new-forecast)))))
