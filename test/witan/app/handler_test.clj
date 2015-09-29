(ns witan.app.handler-test
  (:require [clojure.test :refer :all]
            [compojure.api.test-utils :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [witan.app.handler :refer :all]
            [witan.app.user :as user]
            [clojure.data.json :as json]))

(deftest test-app
  (testing "endpoint unauthorized"
    (let [[status body _] (get* app "/" {})]
      (is (= status 401))
      (is (= body {:error "Unauthorized"}))))

  (testing "login success"
    (with-redefs [user/user-valid? (fn [username password] true)]
      (let [[status body _] (post* app "/login" {:body (json {"username" "support@mastodonc.com" "password" "secret"})})]
        (is (= status 200))
        (is (contains? body :token)))))

  (testing "login failure"
    (with-redefs [user/user-valid? (fn [username password] false)]
      (let [[status body _] (post* app "/login" {:body (json {"username" "blah@blah.blah" "password" "foobar"})})]
        (is (= status 200))
        (is (not (contains? body :token))))))

  (testing "sign up"
    (with-redefs [user/add-user! (fn [username password] ())]
      (let [[status body _] (post* app "/user" {:body (json {"username" "test@test.com" "password" "sekrit"})})]
        (is (= status 201)))
      ))

  (testing "not-found route"
    (let [[status body _] (get* app "/invalid")]
      (is (= status 404)))))
