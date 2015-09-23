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
            [witan.app.user :as user])
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

(def dummy-forecasts [{:id "1234"
                       :name "Population Forecast for Camden"
                       :type :population
                       :n-inputs 3
                       :n-outputs [2 3]
                       :owner "Camden"
                       :version 3
                       :last-modified "Aug 10th, 2015"
                       :last-modifier "Neil"}
                      {:id "1233"
                       :name "Population Forecast for Camden"
                       :type :population
                       :n-inputs 3
                       :n-outputs [2 3]
                       :owner "Camden"
                       :version 2
                       :last-modified "Aug 8th, 2015"
                       :last-modifier "Simon"
                       :descendant-id "1234"}
                      {:id "1232"
                       :name "Population Forecast for Camden"
                       :type :population
                       :n-inputs 3
                       :n-outputs [2 3]
                       :owner "Camden"
                       :version 1
                       :last-modified "July 4th, 2015"
                       :last-modifier "GLA"
                       :descendant-id "1233"}
                      {:id "5678"
                       :name "Population Forecast for Bexley"
                       :type :population
                       :n-inputs 2
                       :n-outputs [3 1]
                       :owner "Bexley"
                       :version 2
                       :last-modified "July 22nd, 2015"
                       :last-modifier "Sarah"}
                      {:id "5676"
                       :name "Population Forecast for Bexley"
                       :type :population
                       :n-inputs 2
                       :n-outputs [3 1]
                       :owner "Bexley"
                       :version 1
                       :last-modified "June 14th, 2015"
                       :last-modifier "Sarah"
                       :descendant-id "5678"}
                      {:id "3339"
                       :name "Population Forecast for Hackney"
                       :type :population
                       :n-inputs 3
                       :n-outputs [2 2]
                       :owner "Hackney"
                       :version 1
                       :last-modified "Feb 14th, 2015"
                       :last-modifier "Deepak"}])

(defn home
  [request]
  (log/info "home was called")
  (if-not (authenticated? request)
    (throw-unauthorized)
    (ok {:message (str "hello " (:identity request))})))

(defn forecasts
  [request]
  (log/info "returning forecasts")
  (if-not (authenticated? request)
    (throw-unauthorized)
    (ok dummy-forecasts)))

(defn forecast
  [id request]
  (log/info "returning forecast" id)
  (if-not (authenticated? request)
    (throw-unauthorized)
    (ok (some #(when (= (:id %) id) %) dummy-forecasts))))

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
  (GET "/forecasts/:id" [id] (partial forecast id))
  (GET "/forecasts" [] forecasts)
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
