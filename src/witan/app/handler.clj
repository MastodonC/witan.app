(ns witan.app.handler
  (:require [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [token-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
            [clojure.tools.logging :as log]
            [compojure
             [core :refer :all]
             [route :as route]]
            [ring.middleware
             [cors :refer [wrap-cors]]
             [defaults :refer [api-defaults wrap-defaults]]
             [json :refer [wrap-json-body wrap-json-response]]]
            [witan.app.user :as user]
            [schema.core :as s]
            [witan.schema :as w]
            [compojure.api.sweet :as sweet]
            [ring.util.http-response :refer :all])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Controllers                                      ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Home page controller (ring handler)
;; If incoming user is not authenticated it raises a not authenticated
;; exception, else simple shows a hello world message.

(defn home
  [request]
  (log/info "home was called")
  (if-not (authenticated? request)
    (throw-unauthorized)
    (ok {:message (str "hello " (:identity request))})))

;; Global storage for store generated tokens.
(def tokens (atom {}))

;; Authenticate Handler
;; Respons to post requests in same url as login and is responsible to
;; identify the incoming credentials and set the appropiate authenticated
;; user into session. `authdata` will be used as source of valid users.

(defn login
  [login-details]
  (let [body (:body login-details)
        username (:username body)
        password (:password body)
        valid? (user/user-valid? username password)]
    (if valid?
      (let [token (user/random-token)]
        (swap! tokens assoc (keyword token) (keyword username))
        (ok {:token token}))
      (ok {:message "login failed"}))))

(defn signup
  [login-details]
  (let [body (:body login-details)
        username (:username body)
        password (:password body)]
    (if (user/add-user! username password)
      (let [token (user/random-token)]
        (swap! tokens assoc (keyword token) (keyword username))
        (ok {:token token}))
      (ok {:message "User already present"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes and Middlewares                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; copied from here https://github.com/JarrodCTaylor/authenticated-compojure-api/tree/master/src/authenticated_compojure_api/middleware
(defn token-auth-mw [handler]
  (fn [request]
    (if (authenticated? request)
      (handler request)
      (unauthorized {:error "Unauthorized"}))))

(defn cors-mw [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (assoc-in [:headers "Access-Control-Allow-Origin"] "*")
          (assoc-in [:headers "Access-Control-Allow-Methods"] "GET, PUT, PATCH, POST, DELETE, OPTIONS")
          (assoc-in [:headers "Access-Control-Allow-Headers"] "Authorization, Content-Type")))))

(defn print-mw
  "for debugging purposes"
  [handler]
  (fn [request]
    (println "LOGGING REQUEST")
    (println (pr-str request))
    (handler request)))

(sweet/defapi app'
  (sweet/GET* "/" []
              :middlewares [cors-mw token-auth-mw]
              {:message "hello"})
  (sweet/POST* "/login" []
         :body [login-details w/LoginDetails]
         :summary "log in"
         :middlewares [cors-mw]
         (login login-details))
  (sweet/POST* "/user" []
               :body [login-details w/LoginDetails]
               :middlewares [cors-mw]
               :summary "sign "
               (signup login-details)))

(defn my-authfn
  [req token]
  (when-let [user (get @tokens (keyword token))]
    user))

;; Create an instance of auth backend.

(def auth-backend
  (token-backend {:authfn my-authfn}))

; the Ring app definition including the authentication backend

(def app (-> app'
             (wrap-authorization auth-backend)
             (wrap-authentication auth-backend)
             (wrap-cors :access-control-allow-origin [#".*"]
                        :access-control-allow-methods [:get :put :post :delete])))
