(ns witan.app.schema
  (:require [schema.core :as s]
            [schema-contrib.core :as sc]))

(defn length-greater [l]
  (s/pred
   (fn [x]
     (> (count x) l))))

(defn matches [r]
  (s/pred
   (fn [s]
          (re-matches r s))))

(defn is-an-email []
  (s/pred
   (fn [s] (re-matches #".*@.*" s))))

(def LoginDetails
  "validation for /login"
  {(s/required-key :username) (s/both (length-greater 5) (is-an-email))
   (s/required-key :password) (length-greater 5)})

(def SignUp
  (merge LoginDetails
         {(s/required-key :name) s/Str}))

(def LoginReturn
  (s/either {(s/required-key :token) s/Str
             (s/required-key :id) s/Uuid}
            {:message s/Str}))

(def IdType
  s/Uuid)

(def DateTimeType
  sc/ISO-Date-Time)

(def User
  "User"
  {(s/required-key :id)   IdType
   (s/required-key :name) s/Str}) ;; TODO add groups

(def ModelPropertyType
  "Types that the model properties can be"
  (s/enum
   "text"
   "number"
   "dropdown"))

(def ModelProperty
  "Properties that a model can expose"
  {(s/required-key :name)    s/Str
   (s/required-key :type)    ModelPropertyType
   (s/optional-key :context) s/Any
   (s/optional-key :enum_values) [s/Str]}) ;; varies depending on the type

(def ModelPropertyValue
  "A model property and value binding"
  {(s/required-key :name)  s/Str
   (s/required-key :value) (s/either s/Str s/Int)}) ;; varies depending on the ModelProperty type

(def NewDataItem
  "new data item being created"
  {(s/required-key :name)      s/Str
   (s/required-key :file-name) s/Str
   (s/required-key :s3-key)    (s/either IdType s/Str)})

(def DataItem
  "A data item"
  {(s/required-key :data-id)   IdType
   (s/required-key :name)      s/Str
   (s/required-key :category)  s/Str
   (s/required-key :publisher) IdType ;; a user or org
   (s/optional-key :version)   s/Int
   (s/optional-key :file-name) s/Str
   (s/optional-key :s3-key)    IdType
   (s/required-key :created)   DateTimeType})

(def ModelInputCategory
  "Inputs into the model"
  s/Str)

(def ModelOutputCategory
  "Outputs from the model"
  s/Str)

(def Model
  "Models are the center-piece of a Forecast"
  {(s/required-key :model-id)    IdType
   (s/required-key :name)        s/Str
   (s/required-key :owner)       IdType
   (s/required-key :version-id)  IdType
   (s/required-key :version)     s/Int
   (s/required-key :created)     DateTimeType
   (s/optional-key :description) s/Str
   (s/optional-key :properties)  [ModelProperty]
   (s/optional-key :input-data) [{(s/required-key :category)    ModelInputCategory
                                  (s/optional-key :description) s/Str
                                  (s/optional-key :default)     DataItem}]
   (s/optional-key :output-data) [{(s/required-key :category) ModelOutputCategory}]
   (s/optional-key :fixed-input-data) [{(s/optional-key :category) ModelInputCategory
                                        (s/optional-key :data) DataItem}]})

(def ModelInput
  "An input category with a data item"
  {ModelInputCategory DataItem})

(def ModelOutput
  "An output category with a data item"
  {ModelOutputCategory [DataItem]})

(def ModelInfo
  "Forecast data in the context of the model"
  {(s/required-key :inputs)     [ModelInput]
   (s/required-key :outputs)    [ModelOutput]
   (s/optional-key :property-values) [ModelPropertyValue]})

(def Tag
  "A tag is an annotated symlink to a forecast id and version"
  {(s/required-key :id)               IdType
   (s/required-key :name)             s/Str
   (s/optional-key :description)      s/Str
   (s/required-key :forecast-id)      IdType
   (s/required-key :forecast-version) s/Int})

(def NewForecast
  "Schema for creating a new forecast"
  {(s/required-key :name)             s/Str
   (s/required-key :model-id)         (s/either IdType s/Str)
   (s/optional-key :description)      s/Str
   (s/optional-key :model-properties) [{s/Keyword s/Str}]})

(def UpdateForecast
  "Schema for creating a new version"
  {(s/optional-key :inputs) {(s/either s/Keyword ModelInputCategory) NewDataItem}})

(def Forecast
  "Forecast"
  {(s/required-key :version-id)    IdType
   (s/required-key :name)          s/Str
   (s/required-key :owner)         IdType
   (s/required-key :owner-name)    s/Str
   (s/required-key :forecast-id)   IdType
   (s/required-key :version)       s/Int
   (s/required-key :created)       DateTimeType
   (s/required-key :in-progress?)  s/Bool
   (s/optional-key :description)   s/Str
   (s/optional-key :tag)           Tag
   (s/optional-key :model-id)      IdType})

(def ForecastInfo
  "Forecast in-depth"
  (merge
   Forecast
   ModelInfo))

(def ShareRequest
  "A request to adjust sharing rules for a tag"
  {(s/optional-key :add)     [sc/Email]
   (s/optional-key :message) s/Str
   (s/optional-key :remove)  [sc/Email]})
