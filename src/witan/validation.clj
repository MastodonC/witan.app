(ns witan.validation
  (:require [clojure.tools.logging :as log]))

(def development-data-category "development-data")

(defn exists?
  [category]
  (some #{category} [development-data-category]))

(defn csv-extension?
  [filename]
  (let [ext (last (clojure.string/split filename #"\."))]
    (= ext "csv")))

(defn header-line-ok?
  [header expected-header]
  (= header expected-header))

(defmulti header-row identity)
(defmethod header-row development-data-category [category]
   ["GSS.Code" "Borough name" "Year" "Past development" "Future development"])

(defn validate
  [category file]
  (with-open [rdr (clojure.java.io/reader file)]
    (let [lines (line-seq rdr)
          header (->> (header-row category) (clojure.string/join ","))
          header-ok (header-line-ok? (first lines) header)]
      [header-ok
       (when-not header-ok {:error (str "The header row of the file is incorrect. we expect " header)})])))

(defn validate-content
  [category file]
  (if (exists? category)
    (validate category file)
    [false {:error "Could not find the validation for this data category."}]))
