(ns rally-api.data-test
  (:require [rally-api.data :as data]
            [clojure.test :refer :all])
  (:import [java.util UUID]
           [java.net URI]))

(deftest convert-from-clojure-to-rally-case
  (is (= "ObjectID" (data/->rally-case :object-id)))
  (is (= "Name" (data/->rally-case :name)))
  (is (= "DirectChildrenCount" (data/->rally-case :direct-children-count)))
  (is (= "_ref" (data/->rally-case :_ref)))
  (is (= "FormattedID" (data/->rally-case :formatted-id)))
  (is (= "_objectVersion" (data/->rally-case :_objectVersion))))

(deftest convert-to-rally-map
  (is (= {:Name "Adam"} (data/->rally-map {:name "Adam"})))
  (is (= {:ObjectID 123} (data/->rally-map {:object-id 123}))))

(deftest convert-clojure-type-to-rally-type
  (is (= "HierarchicalRequirement" (data/clojure-type->rally-type :userstory)))
  (is (= "security" (data/clojure-type->rally-type :security)))
  (is (= "Defect" (data/clojure-type->rally-type :defect))))

(deftest convert-to-clojure-map
  (let [uuid (UUID/randomUUID)]
    (is (= {:query-result {:metadata/type :user :total-result-count 1}} (data/->clojure-map {:QueryResult {:_type "User" :TotalResultCount 1}})))
    (is (= {:query-result {:metadata/type :userstory}} (data/->clojure-map {:QueryResult {:_type "HierarchicalRequirement"}})))
    (is (= {:query-result {:metadata/ref-object-uuid uuid}} (data/->clojure-map {:QueryResult {:_refObjectUUID (str uuid)}})))
    (is (= {:metadata/object-version 123} (data/->clojure-map {:_objectVersion "123"})))
    (is (= {:metadata/ref (URI. "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234")}
           (data/->clojure-map {:_ref "https://localhost/slm/webservice/v2.0/hierarchicalrequirement/1234"})))
    (is (= {:metadata/rally-api-major 2} (data/->clojure-map {:_rallyAPIMajor "2"})))
    (is (= {:metadata/rally-api-minor 0} (data/->clojure-map {:_rallyAPIMinor "0"})))))

(deftest create-fetch-should-translate-names
  (let [fetch [:name :formatted-id :object-id :description]]
    (is (= "Name,FormattedID,ObjectID,Description" (data/create-fetch fetch)))))

(deftest create-fetch-should-passthrough-true
  (let [fetch true]
    (is (= "true" (data/create-fetch fetch)))))

(deftest create-order-should-translate-correctly
  (is (= "Name" (data/create-order [:name])))
  (is (= "Name,Description" (data/create-order [:name :description])))
  (is (= "Name asc" (data/create-order [[:name :asc]])))
  (is (= "Description,Name asc" (data/create-order [:description [:name :asc]])))
  (is (= "Name desc,ObjectID" (data/create-order [[:name :desc] :object-id]))))

(deftest create-query-should-translate-names
  (let [query [:= :formatted-id "S80221"]]
    (is (= "(FormattedID = \"S80221\")" (data/create-query query)))))

(deftest create-query-should-do-proper-nesting
  (is (= "((FormattedID = \"S123\") AND (Name contains \"foo\"))"
         (data/create-query [:and
                             [:= :formatted-id "S123"]
                             [:contains :name "foo"]])))
  (is (= "(((Name = \"Junk\") AND (Age = 34)) AND (Email contains \"test.com\"))"
         (data/create-query [:and
                             [:= :name "Junk"]
                             [:= :age 34]
                             [:contains :email "test.com"]])))
  (is (= "(((Name = \"Junk\") OR (Age = 34)) OR (Email contains \"test.com\"))"
         (data/create-query [:or
                             [:= :name "Junk"]
                             [:= :age 34]
                             [:contains :email "test.com"]])))
  (is (= "(((Name = \"Junk\") AND (Age = 34)) OR (Email contains \"test.com\"))"
         (data/create-query [:or
                             [:and 
                              [:= :name "Junk"]
                              [:= :age 34]]
                             [:contains :email "test.com"]])))
    (is (= "((Name = \"Junk\") OR ((Age = 34) AND (Email contains \"test.com\")))"
         (data/create-query [:or
                             [:= :name "Junk"]
                             [:and 
                              [:= :age 34]
                              [:contains :email "test.com"]]]))))

(deftest convert-to-ref
  (let [ref-str "http://localhost:7001/slm/webservice/v2.0/defect"]
    (is (= ref-str (data/->ref ref-str)))
    (is (= ref-str (data/->ref {:metadata/ref ref-str})))))
