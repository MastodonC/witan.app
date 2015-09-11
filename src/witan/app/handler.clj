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
            [buddy.core
             [codecs :as codecs]
             [nonce :as nonce]]
            [compojure
             [core :refer :all]]
            [compojure
             [core :refer :all]])
  (:gen-class))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Semantic response helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ok [d] {:status 200 :body d})
(defn bad-request [d] {:status 400 :body d})
(defn unauthorized [d] {:status 401 :body d})

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
  [request]
  (let [body (:body request)
        username (:username body)
        password (:password body)
        valid? (user/user-valid? username password)]
    (if valid?
      (let [token (user/random-token)]
        (swap! tokens assoc (keyword token) (keyword username))
        (ok {:token token}))
      (ok {:message "login failed"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Routes and Middlewares                           ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; User defined application routes using compojure routing library.
;; Note: no any middleware for authorization, all authorization system
;; is totally decoupled from main routes.

(defroutes app-routes
  (GET "/" [] home)
  (POST "/login" [] login)
  (route/not-found "Not Found"))

(defn my-authfn
  [req token]
  (when-let [user (get @tokens (keyword token))]
    user))

;; Create an instance of auth backend.

(def auth-backend
  (token-backend {:authfn my-authfn}))

; the Ring app definition including the authentication backend
(def app (-> app-routes
             (wrap-authorization auth-backend)
             (wrap-authentication auth-backend)
             (wrap-json-response {:pretty false})
             (wrap-json-body {:keywords? true :bigdecimals? true})
             (wrap-defaults api-defaults)
             (wrap-cors :access-control-allow-origin [#".*"]
                        :access-control-allow-methods [:get :put :post :delete])))
