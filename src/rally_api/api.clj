(ns rally-api.api
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn-mgr]
            [clj-http.cookies :as cookies]
            [environ.core :as env]
            [rally-api.data :as data]
            [rally-api.request :as request])
  (:use [slingshot.slingshot :only [throw+]])
  (:refer-clojure :exclude [find]))

(defn- not-found? [error]
  (= error "Cannot find object to read"))

(defn- check-for-rally-errors [response]
  (let [errors (:errors response)]
    (cond
     (empty? errors)          response
     (some not-found? errors) nil
     :else                    (throw+ response "rally-errors: %s" errors))))

(defn- do-request [{:keys [middleware request]}]
  (client/with-middleware middleware
    (-> (client/request request)
        :body
        data/->clojure-map
        vals
        first
        check-for-rally-errors)))

(defn create! [rest-api type data]
  (-> rest-api
      (request/set-method :put)
      (request/set-uri type "create")
      (request/set-body-as-map type data)
      do-request
      :object))

(defn update! [rest-api ref-or-object updated-data]
  (let [ref  (data/->ref ref-or-object)
        type (or (:metadata/type ref-or-object) (data/rally-ref->clojure-type ref))]
    (-> rest-api
        (request/set-method :post)
        (request/set-url ref)
        (request/set-body-as-map type updated-data)
        do-request
        :object)))

(defn update-collection! [rest-api collection-ref-or-object action items]
  (let [ref (str (data/->ref collection-ref-or-object) "/" (name action))
        items (map #(hash-map :metadata/ref (data/->ref %)) items)]
    (-> rest-api
        (request/set-method :post)
        (request/set-url ref)
        (request/set-body-as-map :collection-items items)
        do-request)))

(defn delete! [rest-api ref-or-object]
  (-> rest-api
      (request/set-method :delete)
      (request/set-url ref-or-object)
      do-request))

(defn- query-for-page [rest-api uri start pagesize query-spec]
  (-> rest-api
      (request/set-uri uri)
      (request/merge-query-params query-spec)
      (request/set-query-param :start start)
      (request/set-query-param :pagesize pagesize)
      do-request))

(defn query
  ([rest-api uri]
     (query rest-api uri {}))
  ([rest-api uri {:keys [start pagesize] :as query-spec}]
     (let [start              (or start 1)
           pagesize           (or pagesize 200)
           next-start         (+ start pagesize)
           page               (query-for-page rest-api uri start pagesize query-spec)
           total-result-count (:total-result-count page)]
       (concat (:results page)
               (when (<= next-start total-result-count)
                 (lazy-seq (query rest-api uri (assoc query-spec :start next-start))))))))

(defn find
  ([rest-api ref-or-object]
     (-> rest-api
         (request/set-url ref-or-object)
         do-request))
  ([rest-api uri query-spec]
     (-> (query rest-api uri query-spec)
      first)))

(defn find-by-formatted-id [rest-api rally-type formatted-id]
  (let [query-spec {:query [:= :formatted-id formatted-id]
                    :fetch true}]
    ;; We use a query and then search because if you look for formatted-id
    ;; in the artifact endpoint it will return all artifacts with the number
    ;; part of the formatted it. So searching for US11 will also return DE11,T11, and so on.
    (->> (query rest-api rally-type query-spec)
         (some #(when (= formatted-id (:formatted-id %)) %)))))

(defn current-workspace [rest-api]
  (let [current-project (find rest-api (request/get-current-project rest-api))]
    (find rest-api (:workspace current-project))))

(defn current-project [rest-api]
  (find rest-api :project {:fetch true}))

(defn current-user [rest-api]
  (-> rest-api
      (request/set-uri (keyword "user:current"))
      do-request))

(defn- security-token [rest-api {:keys [username password api-key]}]
  (-> rest-api
      (request/set-basic-auth username password)
      (request/set-uri :security :authorize)
      do-request
      :security-token))

(defn- create-rest-api! [{:keys [api-key] :as credentials} rally-host]
  (let [connection-manager (conn-mgr/make-reusable-conn-manager {})
        rest-api           {:request    {:connection-manager connection-manager
                                         :cookie-store       (cookies/cookie-store)
                                         :headers            {"X-RallyIntegrationOS"       (env/env "os.name")
                                                              "X-RallyIntegrationPlatform" (env/env "java.version")
                                                              "X-RallyIntegrationLibrary"  "RallyRestAPIForClojure"}
                                         :debug              (or (env/env :debug-rally-rest) false)
                                         :method             :get
                                         :as                 :json}
                            :rally-host rally-host
                            :middleware client/default-middleware}
        rest-api           (if api-key
                             (request/add-headers rest-api {:zsessionid api-key})
                             (request/set-security-token rest-api (security-token rest-api credentials)))]
    (request/set-current-project rest-api (current-project rest-api))))

(defn create-rest-api
  ([username password rally-host]
   (create-rest-api! {:username username
                      :password password}
                     rally-host))
  ([api-key rally-host]
   (create-rest-api! {:api-key api-key} rally-host))
  ([]
   (let [username   (env/env :username)
         password   (env/env :password)
         api-key    (env/env :api-key)
         rally-host (or (env/env :rally-host) "https://rally1.rallydev.com")]
     (if (and username password)
       (create-rest-api username password rally-host)
       (create-rest-api api-key rally-host)))))

(defn shutdown-rest-api [rest-api]
  (conn-mgr/shutdown-manager (get-in rest-api [:request :connection-manager]))
  (assoc-in rest-api [:request :connection-manager] nil))
