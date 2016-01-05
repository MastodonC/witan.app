(ns witan.app.model-execution
  (:require [amazonica.aws.s3 :as amazonica]
            [witan.app.config :as c]
            [witan.app.s3 :as ws3]
            [clojure-csv.core :as csv]
            [witan.models.dclg :as dclg]
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
  [key tag]
  (log/info "Downloading" key "(tag" tag ")")
  (let [presigned-download-url (ws3/presigned-download-url key "tmp")
        slurped (slurp presigned-download-url)
        result (prepare-data slurped)
        _ (log/info "Finished downloading" key "- rows:" (count result))]
    result))

(defn get-properties
  [forecast]
  (->> (:model_property_values forecast)
       (vals)
       (mapv (comp vec vals))
       (into {})
       (walk/keywordize-keys)))

(defn get-inputs
  "determine which s3 keys to download: given data or input defaults"
  [forecast model]
  (let [input-list (map :category (:input_data model))
        input-defaults (:input_data_defaults model)
        given-inputs (:inputs forecast)
        fixed-inputs (:fixed_input_data model)]
    (merge (into {} (map (fn [d] [(:category d) (:s3_key d)]) fixed-inputs))
           (into {} (map (fn [category] (let  [given-data (get given-inputs category)
                                               default-data (get input-defaults category)]
                                          (cond
                                            given-data   [category (:s3_key given-data)]
                                            default-data [category (:s3_key default-data)]
                                            :else (throw (Exception. (str "Incomplete input data for model: " (:name model))))))) input-list)))))

(defn prepare-download-data
  "prepare download information for all inputs"
  [forecast model]
  (let [inputs (get-inputs forecast model)]
    (log/info "Preparing download information for" (count inputs) "data items (pre cull)...")
    (if-not (some #(-> % second nil?) inputs)
      (into {} (pmap (fn [[category s3-key]] [(keyword category) s3-key]) inputs))
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
      {:s3-key s3-key
       :name data-name
       :file-name (str (slug/->slug data-name) ".csv")
       :category category
       :version 1
       :publisher publisher}
      (throw (Exception. (str "The upload of an output failed:" data-name s3-key))))))

(defmulti execute-model (fn [_ model] [(:name model) (:version model)]))

(defmethod execute-model ["Housing-linked Ward Population Projection Model" 1]
  [forecast model]
  (try
    (let [data (prepare-download-data forecast model)
          properties (get-properties forecast)
          total-outputs (dclg/dclg-housing-linked-model {:properties properties :data data} download)]
      (into {} (map (fn [{:keys [category outputs reports]}]
                      (hash-map category (map #(handle-output
                                                (:owner forecast)
                                                category
                                                %) outputs))) total-outputs)))
    (catch Exception e (do (log/error "Model" (:model_id forecast) "threw an error:" (.getMessage e) (clojure.stacktrace/print-stack-trace e) )
                           {:error (.getMessage e)}))))

(defmethod execute-model ["Trend-based Ward Population Projection Model" 1]
  [forecast model]
  (throw (Exception. (str "The Trend-based Ward Population Projection Model is currently unavailable."))))

(defmethod execute-model :default
  [_ model]
  (throw (Exception. (str "The following model could not be found: " (:name model) " v" (:version model)))))
