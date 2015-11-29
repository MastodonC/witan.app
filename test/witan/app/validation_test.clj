(ns witan.app.validation-test
  (:require [witan.app.validation :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))

(defn get-bad-header-validation [_]
  {:category "development-data" :header-row ["boo" "nonsense"]})
(defn get-dummy-validation [_]
  {:category "development-data" :header-row ["GSS.Code" "Borough name" "Year" "Past development" "Future development"]})

(defn validation-error
  [validated]
  (-> validated second :validation-error))

(deftest content-validation-test

  (testing "no validation found"
    (with-redefs [get-validation (fn [_] nil)]
      (let [validated (validate-content "development-data" {})]
        (is (not (first validated)))
        (is (= "Could not find the validation for this data category." (validation-error validated))))))

  (testing "header validation failed"
    (with-redefs [get-validation get-bad-header-validation]
      (let [validated (validate-content "development-data" (io/file "test-data/development.csv"))]
        (is (not (first validated)))
        (is (= "The header row of the file is incorrect. we expect boo,nonsense"))))))
