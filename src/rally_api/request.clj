(ns rally-api.request
  (:require [cheshire.core :as json]
            [clojure.string :as string]
            [rally-api.data :as data]))

(defn- ->uri-string [rally-host uri]
  (let [uri-seq (if (keyword? uri) [:slm :webservice :v2.0 (data/clojure-type->rally-type uri)] uri)]
    (->> (cons rally-host uri-seq)
         (map name)
         (string/join "/"))))

(defn set-query-param [rest-api name value]
  (assoc-in rest-api [:request :query-params name] value))

(defn get-query-param [rest-api name]
  (get-in rest-api [:request :query-params name]))

(defn- maybe-update-in [m k f]
  (if (contains? m k)
    (assoc m k (f (k m)))
    m))

(defn merge-query-params [rest-api query-params]
  (let [default-params (get-in rest-api [:request :query-params])
        query-params   (-> query-params
                           (maybe-update-in :query data/create-query)
                           (maybe-update-in :order data/create-order)
                           (maybe-update-in :fetch data/create-fetch))
        merged-params  (merge default-params query-params)]
    (assoc-in rest-api [:request :query-params] merged-params)))

(defn set-current-project [rest-api project]
  (assoc-in rest-api [:request :query-params :project] (:metadata/ref project)))

(defn get-current-project [rest-api]
  (get-in rest-api [:request :query-params :project]))

(defn set-security-token [rest-api security-token]
  (assoc-in rest-api [:request :query-params :key] security-token))

(defn set-url [rest-api ref-or-object]
  (assoc-in rest-api [:request :url] (str (data/->ref ref-or-object))))

(defn get-url [rest-api]
  (get-in rest-api [:request :url]))

(defn set-uri [{:keys [rally-host] :as rest-api} uri & additional]
  (let [base-uri   (->uri-string rally-host uri)
        additional (map name additional)
        url        (string/join "/" (cons base-uri additional))]
    (set-url rest-api url)))

(defn add-headers [rest-api headers]
  (update-in rest-api [:request :headers] #(merge % headers)))

(defn set-method [rest-api method]
  (assoc-in rest-api [:request :method] method))

(defn set-basic-auth [rest-api username password]
  (assoc-in rest-api [:request :basic-auth] [username password]))

(defn set-body-as-map [rest-api clojure-type data]
  (let [rally-type (data/clojure-type->rally-type clojure-type)
        body-map   {rally-type data}]
    (assoc-in rest-api [:request :body] (json/generate-string (data/->rally-map body-map)))))

(defn get-body [rest-api]
  (get-in rest-api [:request :body]))
