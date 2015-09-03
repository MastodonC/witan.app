(ns witan.app.handler-test
  (:require [clojure.test :refer :all]
            [ring.mock.request :as mock]
            [ring.util.io :refer [string-input-stream]]
            [witan.app.handler :refer :all]
            [clojure.data.json :as json]))

(defn json-post-request
  [method content]
  (mock/header
   (mock/request :post method (json/write-str content))
     "Content-Type"
     "application/json; charset=utf-8"))

(defn response-body-as-json
  [response]
  (json/read-str (:body response) :key-fn keyword))

(deftest test-app

  (testing "endpoint unauthorized"
    (let [response (app (mock/request :get "/"))]
      (is (= (:status response) 401))
      (is (= (:body response) "Unauthorized"))))

  (testing "login success"
    (let [response (app (json-post-request "/login" {"username" "support@mastodonc.com" "password" "secret"}))]
      (is (= (:status response) 200))
      (is (contains? (response-body-as-json response) :token))))

  (testing "login failure"
    (let [response (app (json-post-request "/login" {"username" "blah@blah.blah" "password" "foobar"}))]
      (is (= (:status response) 400))
      (is (not (contains? (response-body-as-json response) :token)))))

  (testing "not-found route"
    (let [response (app (mock/request :get "/invalid"))]
      (is (= (:status response) 404)))))
