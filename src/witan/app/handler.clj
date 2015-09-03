(ns witan.app.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [clojure.tools.logging :as log]))

(defroutes app-routes
  (GET "/" []
       (log/info "hello world was called")
       "Hello World")
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes api-defaults))
