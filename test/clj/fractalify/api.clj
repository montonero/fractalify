(ns fractalify.api
  (:refer-clojure :exclude [get])
  (:require [fractalify.dev :as dev]
            [com.stuartsierra.component :as c]
            [fractalify.system :as sys]
            [fractalify.mailers.mock-mail-sender :as mms]
            [fractalify.img-cloud.mock-img-cloud :as mic]
            [fractalify.config :as cfg]
            [clj-http.client :as client]
            [plumbing.core :as p]
            [schema.core :as s]
            [fractalify.utils :as u])
  (:use midje.sweet))

(def server-port 10556)
(def server-url (str "http://localhost:" server-port))
(def ^:dynamic *cookies* (clj-http.cookies/cookie-store))

(defn init-test-system []
  (let [deps (merge sys/db-generators-dependencies (sys/new-dependency-map))]
    (-> (sys/new-system-map (merge (cfg/config) {:http-listener {:port server-port}}))
        (assoc :mail-sender (mms/new-mock-mail-sender))
        (assoc :img-cloud (mic/new-mock-img-cloud))
        (sys/generator-components nil)
        (c/system-using deps)
        (dev/init))))

(def stop-system dev/stop)
(def start-system dev/start)

(defn clear-cookies []
  (alter-var-root #'*cookies* (constantly (clj-http.cookies/cookie-store))))

(defn request [path opts]
  (client/request (merge {:url              (str server-url path)
                          #_"http://requestb.in/xsd0u1xs"
                          :headers          {"Accept" "application/transit+json"}
                          :content-type     :application/transit+json
                          :force-redirects  true
                          :throw-exceptions false
                          :as               :transit+json
                          :cookie-store     *cookies*}
                         opts)))

(defn get
  ([path] (get path {}))
  ([path opts]
   (request path (merge {:method :get} opts))))

(defn post
  ([path body] (post path body {}))
  ([path body opts]
   (request path (merge {:method      :post
                         :form-params body} opts))))

(defn put
  ([path body] (put path body {}))
  ([path body opts]
   (request path (merge {:method      :put
                         :form-params body} opts))))

(defn delete
  ([path] (delete path {}))
  ([path opts]
   (request path (merge {:method :delete} opts))))

(defn status-checker [expected-status]
  (p/fnk [status & _]
    (= status expected-status)))

(defn response-schema [schema]
  (p/fnk [body & _]
    (s/validate schema body)))

(defn resp-has-map? [m]
  (p/fnk resp-has-map [body & _]
    (u/submap? m body)))

(def get-list-items (p/fn-> :body :items))

(defn list-resp-has-map? [m]
  (fn resp-has-map [res]
    (some #(u/submap? m %) (get-list-items res))))

(defn list-resp-items-total? [n]
  (p/fnk [[:body total-items] & _]
    (= n total-items)))

(defn list-resp-items-count? [n]
  (fn [res]
    (= n (count (get-list-items res)))))

(def status-ok (status-checker 200))
(def created (status-checker 201))
(def no-content (status-checker 204))
(def bad-request (status-checker 400))
(def unauthorized (status-checker 401))
(def forbidden (status-checker 403))
(def not-found (status-checker 404))
(def conflict (status-checker 409))