(ns witan.app.data
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.tools.logging :as log]
            [witan.app.config :as c]
            [schema.core :as s]
            [witan.app.schema :as ws]
            [witan.app.validation :as validation]
            [witan.app.util :as util]
            [witan.app.s3 :as s3])
  (:use [liberator.core :only [defresource]]))

(defn ->Data
  ([data]
   (->Data data false))
  ([{:keys [data_id
            file_name
            s3_key
            created
            public] :as data} url?]
   (let [new-data (-> data
                      (dissoc :data_id
                              :file_name
                              :s3_key
                              :public
                              :created)
                      (assoc :data-id data_id
                             :file-name file_name
                             :s3-key s3_key
                             :public? public
                             :created (util/java-Date-to-ISO-Date-Time created)))]
     (if url?
       (assoc new-data :s3-url (str (s3/presigned-download-url s3_key file_name)))
       new-data))))

(defn find-data-by-category
  [category]
  (hayt/select :data_by_category (hayt/where {:category category})))

(defn find-data-by-data-id
  [data-id]
  (hayt/select :data_by_data_id (hayt/where {:data_id data-id})))

(defn find-data-name
  [name]
  (hayt/select :data_names (hayt/where {:name name})))

(defn find-data-by-s3-key
  [s3-key]
  (hayt/select :data_by_s3_key (hayt/where {:s3_key s3-key})))

(defn update-version-number-name
  [name version]
  (hayt/update :data_names (hayt/set-columns {:version version})
                           (hayt/where {:name name})))

(defn get-current-version-name
  [name]
  (try
    (some-> (first (c/exec (find-data-name name)))
            :version)
    (catch Exception e nil)))

(defn get-data-by-s3-key
  [s3-key]
  (-> s3-key
      (find-data-by-s3-key)
      (c/exec)
      (first)))

(defn get-data-by-category
  [category user]
  (filter #(or (:public %)
               (= (:publisher %) user))
          (c/exec (find-data-by-category category))))

(defn data-to-db
  [{:keys [data-id category name publisher version file-name s3-key public?] :as data}]
  (-> data
      (dissoc :data-id
              :file-name
              :s3-key
              :public?)
      (assoc :data_id data-id
             :file_name file-name
             :s3_key s3-key
             :public public?)))

(defn create-data
  [{:keys [data-id category name publisher version file-name s3-key public?]} data-table]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert data-table (hayt/values :data_id data-id
                                         :category category
                                         :name name
                                         :publisher publisher
                                         :version version
                                         :file_name file-name
                                         :public public?
                                         :s3_key s3-key
                                         :created creation-time))))

(defn add-data!
  "add data version"
  [{:keys [data-id category name file-name publisher s3-key public?]
    :or {data-id (uuid/random)
         public? false}}] ;; always default public to false.
  (let [current-version (get-current-version-name name)
        version (if current-version (inc current-version) 1)]
    (run! #(c/exec (create-data {:data-id data-id
                                 :category category
                                 :name name
                                 :file-name file-name
                                 :publisher publisher
                                 :version version
                                 :public? public?
                                 :s3-key s3-key} %)) '(:data_by_data_id :data_by_category :data_by_s3_key))
    (c/exec (update-version-number-name name version))
    (first (c/exec (find-data-by-data-id data-id)))))

(defresource search [{:keys [category]}]
  util/json-resource
  :allow-methods #{:get}
  :handle-ok (fn [ctx]
               (s/validate [ws/DataItem] (map #(->Data % true) (get-data-by-category
                                                                category
                                                                (util/get-user-id ctx))))))

(defresource data [{:keys [category name file public user-id]}]
  :allowed-methods #{:post}
  :available-media-types ["application/json"]
  :processable? (fn [ctx]
                  (log/info public)
                  (cond
                    (not (validation/csv-extension? (:filename file))) [false {:error "this doesn't seem to be a csv file"}]
                    :default (validation/validate-content category (:tempfile file))))
  :handle-unprocessable-entity (fn [ctx] {:error (:error ctx)})
  :handle-created (fn [ctx]
                    (let [s3-key (java.util.UUID/randomUUID)
                          post-params (util/get-post-params ctx)]
                      (try (s3/upload s3-key (:tempfile file))
                           (->Data (add-data! {:category category
                                               :name name
                                               :file-name (:filename file)
                                               :s3-key s3-key
                                               :public? public
                                               :publisher user-id}))
                           (catch com.amazonaws.AmazonClientException e [false {:error "File upload failed"}])))))
