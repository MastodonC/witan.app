(ns witan.app.s3-test
  (:require [witan.app.s3 :refer :all]
            [clojure.test :refer :all]))

(deftest presigned-url-test
  (testing "output matches output required by s3-beam"
    (let [file-name "subpath/my-file.csv"
          json (s3-beam-format (presigned-url file-name) file-name)
          _ (println json)]
      (is (:signature json))
      (is (:policy json))
      (is (= (:acl json) "public-read"))
      (is (= (:key json) file-name)))))

(comment "default s3 beam handler produces following output:"
         {:action "https://witan-test-data.s3-eu-central-1.amazonaws.com/"
          :key nil
          :Content-Type nil
          :policy "eyJleHBpcmF0aW9uIjoiMjAxNS0xMC0yM1QxMToxNzowNS4wMDBaIiwiY29uZGl0aW9ucyI6W3siYnVja2V0Ijoid2l0YW4tdGVzdC1kYXRhIn0seyJhY2wiOiJwdWJsaWMtcmVhZCJ9LFsic3RhcnRzLXdpdGgiLCIkQ29udGVudC1UeXBlIixudWxsXSxbInN0YXJ0cy13aXRoIiwiJGtleSIsbnVsbF0seyJzdWNjZXNzX2FjdGlvbl9zdGF0dXMiOiIyMDEifV19"
          :acl "public-read"
          :success_action_status "201"
          :AWSAccessKeyId "AKIAJW7X5ZJ5NTJ3N73Q"
          :signature "zGtSw+CMpXp6jROGH/uOd3fRogs="})
