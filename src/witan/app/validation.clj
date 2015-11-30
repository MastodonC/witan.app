(ns witan.app.validation
  (:require [clojure.tools.logging :as log]
            [qbits.hayt :as hayt]
            [witan.app.config :as c])
  (:use [liberator.core :only [defresource]]))

(defn find-validation
  [category]
  (hayt/select :validations (hayt/where {:category category})))

(defn get-validation
  [category]
  (first (c/exec (find-validation category))))

(defn add-validation!
  [{:keys [category header-row]}]
  (c/exec (hayt/insert :validations (hayt/values {:category category
                                                  :header_row header-row}))))
(defn csv-extension?
  [filename]
  (let [ext (last (clojure.string/split filename #"\."))]
    (= ext "csv")))

(defn header-line-ok?
  [header expected-header]
  (= header (clojure.string/join "," expected-header)))

(defn validate
  [validation file]
  (with-open [rdr (clojure.java.io/reader file)]
    (let [lines (line-seq rdr)]
      (log/info (first lines))
      [(header-line-ok? (first lines) (:header_row validation))
       {:error (str "The header row of the file is incorrect. we expect " (:header_row validation))}])))

(defn validate-content
  [category file]
  (if-let [validation (get-validation category)]
    (validate validation file)
    [false {:error "Could not find the validation for this data category."}]))
