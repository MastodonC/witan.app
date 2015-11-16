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

(def borough-gss-code
  {"Barking and Dagenham" "E09000002"
   "Barnet" "E09000003"
   "Bexley" "E09000004"
   "Brent" "E09000005"
   "Bromley" "E09000006"
   "Camden" "E09000007"
   "City of London" "E09000001"
   "Croydon" "E09000008"
   "Ealing" "E09000009"
   "Enfield" "E09000010"
   "Greenwich" "E09000011"
   "Hackney" "E09000012"
   "Hammersmith and Fulham" "E09000013"
   "Haringey" "E09000014"
   "Harrow" "E09000015"
   "Havering" "E09000016"
   "Hillingdon" "E09000017"
   "Hounslow" "E09000018"
   "Islington" "E09000019"
   "Kensington and Chelsea" "E09000020"
   "Kingston upon Thames" "E09000021"
   "Lambeth" "E09000022"
   "Lewisham" "E09000023"
   "Merton" "E09000024"
   "Newham" "E09000025"
   "Redbridge" "E09000026"
   "Richmond upon Thames" "E09000027"
   "Southwark" "E09000028"
   "Sutton" "E09000029"
   "Tower Hamlets" "E09000030"
   "Waltham Forest" "E09000031"
   "Wandsworth" "E09000032"
   "Westminster" "E09000033"})

(defn gss-code [borough]
  (get borough-gss-code borough))

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

(defn borough-property-to-gss [properties]
  (assoc properties :borough (gss-code (:borough properties)) ))

(defmulti execute-model (fn [_ model] [(:name model) (:version model)]))
(defmethod execute-model ["DCLG-based Housing Linked Model" 1]
  [forecast model]
  (let [data (download-data forecast model)
        _ (log/info "Downloads finished. Calculating...")
        properties (borough-property-to-gss (get-properties forecast))
        outputs (m/dclg-housing-linked-model (merge properties data))]
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
