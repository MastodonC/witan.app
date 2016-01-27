(ns witan.app.data
  (:require [qbits.hayt :as hayt]
            [qbits.alia.uuid :as uuid]
            [clj-time.core :as t]
            [clj-time.format :as tf]
            [clojure.tools.logging :as log]
            [witan.app.config :as c]
            [schema.core :as s]
            [witan.app.schema :as ws]
            [witan.validation :as validation]
            [witan.app.util :as util]
            [witan.app.s3 :as s3]
            [witan.app.user :as usr])
  (:use [liberator.core :only [defresource]]))

(defn ->Data
  ([data]
   (->Data data false))
  ([{:keys [data_id
            file_name
            s3_key
            created
            public
            publisher_name] :as data} url?]
   (let [new-data (-> data
                      (dissoc :data_id
                              :file_name
                              :s3_key
                              :public
                              :created
                              :publisher_name)
                      (assoc :data-id data_id
                             :file-name file_name
                             :publisher-name publisher_name
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

(defn exists?
  [data]
  (-> data
      :s3-key
      (get-data-by-s3-key)))

(defn get-data-by-category
  [category user]
  (filter #(or (:public %)
               (= (:publisher %) user))
          (c/exec (find-data-by-category category))))

(defn get-data-by-data-id
  [data-id]
  (let [id (if (string? data-id) (java.util.UUID/fromString data-id) data-id)]
    (first (c/exec (find-data-by-data-id id)))))

(defn data-to-db
  [{:keys [data-id file-name s3-key public? publisher-name] :as data}]
  (-> data
      (dissoc :data-id
              :file-name
              :s3-key
              :public?
              :publisher-name)
      (assoc :data_id data-id
             :file_name file-name
             :s3_key s3-key
             :public public?
             :publisher_name publisher-name)))

(defn create-data
  [{:keys [data-id category name publisher publisher-name version file-name s3-key public?]} data-table]
  (let [creation-time (tf/unparse (tf/formatters :date-time) (t/now))]
    (hayt/insert data-table (hayt/values :data_id data-id
                                         :category category
                                         :name name
                                         :publisher publisher
                                         :version version
                                         :file_name file-name
                                         :public public?
                                         :s3_key s3-key
                                         :created creation-time
                                         :publisher_name publisher-name))))

(defn add-data!
  "add data version"
  [{:keys [data-id category name file-name publisher s3-key public?]
    :or {data-id (uuid/random)
         public? false}}] ;; always default public to false.
  (let [current-version (get-current-version-name name)
        user-name (:name (usr/retrieve-user publisher))
        version (if current-version (inc current-version) 1)]
    (run! #(c/exec (create-data {:data-id data-id
                                 :category category
                                 :name name
                                 :file-name file-name
                                 :publisher publisher
                                 :publisher-name user-name
                                 :version version
                                 :public? public?
                                 :s3-key s3-key} %)) '(:data_by_data_id :data_by_category :data_by_s3_key))
    (c/exec (update-version-number-name name version))
    (get-data-by-data-id data-id)))

(defresource search [{:keys [category groups]}]
  util/json-resource
  :allow-methods #{:get}
  :handle-ok (fn [ctx]
               (s/validate [ws/DataItem] (map #(->Data % true) (get-data-by-category
                                                                category
                                                                (when-not (contains? groups "public") (util/get-user-id ctx)))))))

(defresource data [{:keys [category name file public user-id]}]
  :allowed-methods #{:post}
  :available-media-types ["application/json"]
  :processable? (fn [ctx]
                  (cond
                    (not (validation/csv-extension? (:filename file))) [false {:error "this doesn't seem to be a csv file"}]
                    :default (validation/validate-content category (:tempfile file))))
  :handle-unprocessable-entity (fn [ctx] {:error (:error ctx)})
  :post! (fn [ctx]
           (let [s3-key (java.util.UUID/randomUUID)]
             (try (s3/upload s3-key (:tempfile file))
                  {:s3-key s3-key}
                  (catch com.amazonaws.AmazonClientException e [false {:error "File upload failed"}]) )))
  :handle-created (fn [ctx]
                    (let [post-params (util/get-post-params ctx)]
                      (->Data (add-data! {:category category
                                          :name name
                                          :file-name (:filename file)
                                          :s3-key (:s3-key ctx)
                                          :public? public
                                          :publisher user-id})))))

(defresource public [filename redirect]
  util/json-resource
  :allow-methods #{:get}
  :exists? (fn [_] (if redirect
                     false
                     (s3/exists? (str "public/" filename))))
  :handle-ok {:location (str (s3/presigned-download-url
                              (str "public/" filename)
                              filename))}
  :existed? (fn [ctx]
              (s3/exists? (str "public/" filename)))
  :moved-permanently? (fn [_] false)
  :moved-temporarily? {:location (s3/presigned-download-url
                                  (str "public/" filename)
                                  filename)})
