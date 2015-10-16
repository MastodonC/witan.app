(ns witan.app.forecast-test
  (:require [witan.app.forecast :refer :all]
            [witan.app.model :as model]
            [clojure.test :refer :all]
            [qbits.hayt :as hayt]))


(deftest test-property-values-handling
  (testing "numerical property"
    (with-redefs [model/get-model-by-model-id (fn [_]
                                                {:properties [{:name "number field" :type "number" :context "this is a number field"}]})]
      (testing "valid numerical property"
        (let [checked-property-values (check-property-values "fake-model-id" [{:name "number field" :value "123"}])]
          (is (= (:values checked-property-values) {"number field" (hayt/user-type {:name "number field":value "123"})}))
          (is (empty? (:errors checked-property-values)))))
      (testing "invalid numerical property"
        (let [checked-property-values (check-property-values "fake-model-id" [{:name "number field" :value "boo123"}])]
          (is (= (:errors checked-property-values) ["Wrong type for number field"]))
          (is (empty? (:values checked-property-values)))))))
  (testing "text property"
    (with-redefs [model/get-model-by-model-id (fn [_]
                                                {:properties [{:name "text field" :type "text" :context "this is a text field"}]})]
      (let [checked-property-values (check-property-values "fake-model-id" [{:name "text field" :value "wonderful world"}])]
        (is (= (:values checked-property-values) {"text field" (hayt/user-type {:name "text field" :value "wonderful world"})})))))
  (testing "dropdown property"
    (with-redefs [model/get-model-by-model-id (fn [_]
                                                {:properties [{:name "Boroughs" :type "dropdown" :context "this is a dropdown field" :enum_values ["Barnet" "Camden" "Southwark"]}]})]
      (testing "allowed dropdown value"
        (let [checked-property-values (check-property-values "fake-model-id" [{:name "Boroughs" :value "Camden"}])]
          (is (= (:values checked-property-values) {"Boroughs" (hayt/user-type {:name "Boroughs" :value "Camden"})}))
          (is (empty? (:errors checked-property-values))))))))
