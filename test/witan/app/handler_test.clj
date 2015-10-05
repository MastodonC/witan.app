(ns witan.app.handler-test
  (:require [clojure.test :refer :all]
            [compojure.api.test-utils :refer :all]
            [ring.util.io :refer [string-input-stream]]
            [witan.app.handler :refer :all]
            [witan.app.user :as user]
            [clojure.data.json :as json]))

(def user-id (java.util.UUID/randomUUID))

(defn logged-in-user-token []
  (with-redefs [user/user-valid? (fn [username password] {:id user-id})]
    (let [[_ body _] (post* app "/api/login" {:body (json {"username" "test@test.com" "password" "secret"})})]
      (:token body))))

(deftest test-app
  (testing "/api/"
    (testing "endpoint unauthorized"
      (let [[status body _] (get* app "/api/" {})]
        (is (= status 401))
        (is (= body {:error "Unauthorized"})))))
  (testing "/api/login"
    (testing "login success"
      (with-redefs [user/user-valid? (fn [username password] {:id user-id})]
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
      (with-redefs [user/user-valid? (fn [username password] {:id user-id})]
        (let [[_ login-body _] (post* app "/api/login" {:body (json {"username" "support@mastodonc.com" "password" "secret"})})
              token (:token login-body)
              [status body _] (get* app "/api/" {}  {"Authorization" (str "Token " token)})]
          (is (= status 200))
          (is (contains? body :message))))))

  (testing "/api/user"
    (testing "sign up"
      (with-redefs [user/add-user! (fn [user] ())]
        (let [[status body _] (post* app "/api/user" {:body (json {"username" "test@test.com" "password" "sekrit" "name" "Arthur Dent"})})]
          (is (= status 201))
          (is (contains? body :token))
          (is (contains? body :id)))
        )))

  (testing "/api/me"
    (testing "401 when not logged in"
      (with-redefs [user/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [[status body _] (get* app "/api/me" {})]
          (is (= status 401)))))

    (testing "retrieve user"
      (with-redefs [user/retrieve-user (fn [id] {:id id :name "Joe" :username "joe@test.com"})]
        (let [token (logged-in-user-token)
              [status body _] (get* app "/api/me" {} {"Authorization" (str "Token " token)})]
          (is (= status 200))))))

  (testing "not-found route"
    (let [[status body _] (get* app "/invalid")]
      (is (= status 404)))))
