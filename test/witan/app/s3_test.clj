(ns witan.app.s3-test
  (:require [witan.app.s3 :refer :all]
            [clojure.test :refer :all]))

(deftest presigned-url-test
  (testing "output matches output required by s3-beam"
    (let [file-name "subpath/my-file.csv"
          json (s3-beam-format (presigned-url file-name) file-name)]
      (is (:X-Amz-Signature json))
      (is (:X-Amz-Credential json))
      (is (:X-Amz-Date json))
      (is (:X-Amz-Expires json))
      (is (:X-Amz-SignedHeaders json)))))
