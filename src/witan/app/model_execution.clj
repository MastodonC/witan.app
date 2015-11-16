(ns witan.app.model-execution
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [witan.app.s3 :as ws3]
            [clojure-csv.core :as csv]
            [witan.models :as m]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [clojure.core.async :as a :refer [<!! timeout]]
            [clj-http.client :as client]
            [slugger.core :as slug]))

(comment "Striving to be as independent as possible from the other witan app namespaces,
since the plan is eventually to have this running on separate docker instances.
TODO: will need to get own config files")

(def bucket
  (-> c/config :s3 :bucket))

(defn prepare-data
  [data]
  (let [normalised-data (-> data
                            (clojure.string/replace "\r\n" "\n")
                            (clojure.string/replace "\r"   "\n"))
        parsed-csv (csv/parse-csv normalised-data :end-of-line nil)
        parsed-data (rest parsed-csv)
        headers (map str/lower-case (first parsed-csv))]
    (map #(walk/keywordize-keys (zipmap headers %1)) parsed-data)))

(defn download
  "Gets the content of an object
   NOTE ASSUMPTION: the input files are csv with \n or \r\n as eol characters"
  [key]
  (let [presigned-download-url (ws3/presigned-download-url key "tmp")
        slurped (slurp presigned-download-url)]
    (prepare-data slurped)))

(defn get-properties
  [forecast]
  (->> (:model_property_values forecast)
       (mapv second)
       (into {})))

(defn get-inputs
  "determine which s3 keys to download: given data or input defaults"
  [forecast model]
  (let [input-list (map :category (:input_data model))
        input-defaults (:input_data_defaults model)
        given-inputs (:inputs forecast)
        fixed-inputs (:fixed_input_data model)]
    (merge (into {} (map (fn [[k v]] [k (:s3_key v)]) fixed-inputs))
           (into {} (map (fn [category] (let  [given-data (get given-inputs category)
                                               default-data (get input-defaults category)]
                                          (cond
                                            given-data   [category (:s3_key given-data)]
                                            default-data [category (:s3_key default-data)]
                                            :else (throw (Exception. (str "Incomplete input data for model: " (:name model))))))) input-list)))))

(defn download-data
  "download all inputs"
  [forecast model]
  (let [inputs (get-inputs forecast model)]
    (log/info "Downloading" (count inputs) "data items...")
    (if-not (some #(-> % second nil?) inputs)
      (into {} (pmap (fn [[category s3-key]] [(keyword category) (download s3-key)]) inputs))
      (throw (Exception. (str "One or more download keys provided were nil."))))))

(defn handle-output
  [publisher category raw-output]
  (let [data-name (:name raw-output)
        titles (->> raw-output :data first keys (map name))
        data   (map #(map str %) (->> raw-output :data (map vals)))
        output (concat [titles] data)
        csv    (csv/write-csv output)
        s3-key (ws3/get-new-s3-key)
        presigned-upload-url (ws3/presigned-upload-url s3-key)
        http-resp (client/put (str presigned-upload-url) {:body csv})]
    (if (= (:status http-resp) 200)
      [{:s3-key s3-key
        :name data-name
        :file-name (str (slug/->slug data-name) ".csv")
        :category category
        :version 1
        :publisher publisher}] ;; TODO db expects a list
      (throw (Exception. (str "The upload of an output failed:" data-name s3-key))))))

(defmulti execute-model (fn [_ model] [(:name model) (:version model)]))
(defmethod execute-model ["DCLG-based Housing Linked Model" 1]
  [forecast model]
  (let [data (download-data forecast model)
        _ (log/info "Downloads finished. Calculating...")
        properties (get-properties forecast)
        outputs (m/dclg-housing-linked-model data)]
    (map (fn [[category output]] (hash-map category
                                           (handle-output
                                            (:owner forecast)
                                            category
                                            output))) outputs)))
(defmethod execute-model :default
  [_ model]
  (throw (Exception. (str "The following model could not be found: " (:name model) " v" (:version model)))))

(defn execute-test-model
  [forecast model]
  (let [properties (get-properties forecast)]
    (log/info "Running the test model with the following properties: " properties)
    (<!! (timeout 3000))
    {"Some output" [{:name      "Fake result"
                     :file_name "fake.csv"
                     :s3_key    #uuid "0c53f871-ba4e-4e90-81df-425382e9b95e"}]
     "Some more output" [{:name      "Fake result 2"
                          :file_name "fake2.csv"
                          :s3_key    #uuid "1c53f871-ba4e-4e90-81df-425382e9b95e"}
                         {:name      "Fake result 3"
                          :file_name "fake3.csv"
                          :s3_key    #uuid "2c53f871-ba4e-4e90-81df-425382e9b95e"}]}))
