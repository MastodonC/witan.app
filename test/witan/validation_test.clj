(ns witan.validation-test
  (:require [witan.validation :refer :all]
            [clojure.test :refer :all]
            [clojure.java.io :as io]))

(defn validation-error
  [validated]
  (-> validated second :error))

(deftest content-validation-test

  (testing "no validation found"
    (let [validated (validate-content "dummy-data" {})]
      (is (not (first validated)))
      (is (= "Could not find the validation for this data category." (validation-error validated)))))

  (testing "header validation failed"
    (let [validated (validate-content "development-data" (io/file "test-data/bad-header.csv"))]
      (is (not (first validated)))
      (is (= "The header row of the file is incorrect. we expect GSS.Code,Borough name,Year,Past development,Future development" (validation-error validated)))))

  (testing "validation ok"
    (let [validated (validate-content "development-data" (io/file "test-data/development.csv"))]
      (is (first validated))
      (is (nil? (second validated))))))
