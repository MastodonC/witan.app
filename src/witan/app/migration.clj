(ns witan.app.migration
  (:require [qbits.hayt :as hayt]
            [witan.app.config :as c]
            [witan.app.user :as usr]
            [witan.app.schema :as ws]
            [schema.core :as s]))

(defn- add-user-name-to-tables
  [[table {:keys [column user-column primary-keys]}]]
  (let [check (-> (hayt/select table) (c/exec) (first) (keys) (set))]
    (if-not (contains? check column)
      (let [results (c/exec (hayt/select table))]
        (println "Adding column" column "to table" table)
        (c/exec (hayt/alter-table table (hayt/add-column column :text)))
        (run! #(let [user-id (get % user-column)
                     primary-key-results (into {} (map (fn [x] (hash-map x (get % x))) primary-keys))
                     user-name (:name (usr/retrieve-user user-id))
                     cmd (hayt/update table (hayt/set-columns {column user-name}) (hayt/where primary-key-results))]
                 (println "Adding" column user-name "to" (:name %))
                 (c/exec cmd)) results))
      (println "Skipping" table "as column" column "already exists..."))))

(defn- add-user-name-to-data-items
  [[table {:keys [columns primary-keys]}]]
  (let [new-column :publisher_name
        user-id-column :publisher
        additional-schema {(s/required-key new-column) s/Str}
        results (c/exec (hayt/select table))]
    (run! (fn [{:keys [column type]}]
            (run! (fn [result]
                    (let [data (get result column)
                          primary-key-results (into {} (map (fn [x] (hash-map x (get result x))) primary-keys))
                          new-data (condp = type
                                     :map-text-data-item
                                     (->> data
                                          (map (fn [[k v]]
                                                 (let [user-name (:name (usr/retrieve-user (get v user-id-column)))]
                                                   (hash-map k (hayt/user-type (assoc v new-column user-name))))))
                                          (into {}))
                                     :map-text-list-data-item
                                     (->> data
                                          (map (fn [[k v]]
                                                 (hash-map k (mapv #(let [user-name (:name (usr/retrieve-user (get % user-id-column)))]
                                                                      (hayt/user-type (assoc % new-column user-name))) v))))
                                          (into {}))
                                     :list-data-item
                                     (mapv #(let [user-name (:name (usr/retrieve-user (get % user-id-column)))]
                                              (hayt/user-type (assoc % new-column user-name))) data))]
                      (when (not-empty new-data)
                        (println "Adding" new-column "to" table column "where" primary-key-results)
                        (c/exec (hayt/update table (hayt/set-columns {column new-data}) (hayt/where primary-key-results)))))) results)) columns)))

(defn add-user-names!
  []
  (let [top-tables {:data_by_category {:column :publisher_name :user-column :publisher :primary-keys [:data_id :category]}
                    :data_by_data_id  {:column :publisher_name :user-column :publisher :primary-keys [:data_id]}
                    :data_by_s3_key   {:column :publisher_name :user-column :publisher :primary-keys [:data_id :s3_key]}
                    :models           {:column :owner_name :user-column :owner :primary-keys [:model_id]}}
        data-item-tables {:forecasts {:columns [{:column :inputs :type :map-text-data-item}
                                                {:column :outputs :type :map-text-list-data-item}]
                                      :primary-keys [:forecast_id :version]}
                          :models {:columns [{:column :input_data_defaults :type :map-text-data-item}
                                             {:column :fixed_input_data :type :list-data-item}]
                                   :primary-keys [:model_id]}}]
    ;; add to tables
    (run! add-user-name-to-tables top-tables)
    ;; add to data_item type
    (c/exec (hayt/alter-type :data_item (hayt/add-column :publisher_name :text)))
    ;; update embedded data items
    (run! add-user-name-to-data-items data-item-tables)))
