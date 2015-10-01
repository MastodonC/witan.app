(ns witan.app.handler-test
  (:require [clojure.test :refer :all]
            [compojure.api.test-utils :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [witan.app.handler :refer :all]
            [witan.app.user :as user]
            [clojure.data.json :as json]))

(deftest test-app
  (testing "endpoint unauthorized"
    (let [[status body _] (get* app "/api/" {})]
      (is (= status 401))
      (is (= body {:error "Unauthorized"}))))

  (testing "login success"
    (with-redefs [user/user-valid? (fn [username password] true)]
      (let [[status body _] (post* app "/api/login" {:body (json {"username" "support@mastodonc.com" "password" "secret"})})]
        (is (= status 200))
        (is (contains? body :token))
        (is (contains? body :id)))))

  (testing "login failure"
    (with-redefs [user/user-valid? (fn [username password] false)]
      (let [[status body _] (post* app "/api/login" {:body (json {"username" "blah@blah.blah" "password" "foobar"})})]
        (is (= status 200))
        (is (not (contains? body :token)))
        (is (not (contains? body :id))))))

  (testing "logged in user"
    (with-redefs [user/user-valid? (fn [username password] true)]
      (let [[_ login-body _] (post* app "/api/login" {:body (json {"username" "support@mastodonc.com" "password" "secret"})})
            token (:token login-body)
            [status body _] (get* app "/api/" {}  {"Authorization" (str "Token " token)})]
        (is (= status 200))
        (is (contains? body :message)))))


  (testing "sign up"
    (with-redefs [user/add-user! (fn [username password] ())]
      (let [[status body _] (post* app "/api/user" {:body (json {"username" "test@test.com" "password" "sekrit" "name" "Arthur Dent"})})]
        (is (= status 201)))
      ))

  (testing "not-found route"
    (let [[status body _] (get* app "/invalid")]
      (is (= status 404)))))
