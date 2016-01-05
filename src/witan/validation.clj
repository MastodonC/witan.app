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

(defmulti category-valid?
  (fn [category lines] category))

(defmethod category-valid? development-data-category
  [category lines]
  (let [req-headers #{"GSS Code Borough" "Borough Name" "GSS Code Ward" "Ward Name"}
        inc-headers (set (clojure.string/split (first lines) #","))]
    (when-not (empty? (clojure.set/difference req-headers inc-headers))
      (str "The header row of the file is missing one or more required rows. We expect " (clojure.string/join "," req-headers)))))

(defn validate
  [category file]
  (with-open [rdr (clojure.java.io/reader file)]
    (let [lines (line-seq rdr)
          error? (category-valid? category lines)]
      [(nil? error?) (when error? {:error error?})])))

(defn validate-content
  [category file]
  (if (exists? category)
    (validate category file)
    [false {:error "Could not find the validation for this data category."}]))
