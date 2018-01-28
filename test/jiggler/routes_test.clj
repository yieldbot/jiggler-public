;; Copyright (c) 2017 Yieldbot
(ns jiggler.routes-test
  (:require
   [clojure.test :refer :all]
   [com.stuartsierra.component :as component]
   [jiggler.db :as db]
   [jiggler.routes :as routes]
   [net.cgrand.enlive-html :as html]
   [ring.mock.request :as mock]))

(defn mk-routes []
  (:routes (component/start (assoc (routes/make-routes "http://CANONICAL/") :database nil))))

(defn body-str [resp]
  (-> resp
      :body
      ((partial apply str))))

(defn body-html [resp]
  (-> resp
      body-str
      .getBytes
      java.io.ByteArrayInputStream.
      html/xml-parser))

(defn input-vals [html input-name]
  (as-> html $
        (html/select $ [[:input (html/attr= :name input-name)]])
        (map #(get-in % [:attrs :value]) $)))

(defn html? [resp]
  (re-matches #"^text/html.*" (get-in resp [:headers "Content-Type"])))

(deftest test-root
  (let [resp ((mk-routes) (mock/request :get "/"))]
    (is (= 307 (:status resp)))
    (is (= "http://CANONICAL/_/link" (get-in resp [:headers "Location"])))))

(deftest test-underscore
  (let [resp ((mk-routes) (mock/request :get "/_/"))]
    (is (= 307 (:status resp)))
    (is (= "http://CANONICAL/_/link" (get-in resp [:headers "Location"])))))

(deftest test-link-empty
  (let [resp ((mk-routes) (mock/request :get "/_/link"))]
    (is (= 200 (:status resp)))
    (is (html? resp))
    ;; No default form values filled
    (is (= [nil nil]
           (-> resp body-html (input-vals "shortlink"))))
    (is (= [nil]
           (-> resp body-html (input-vals "target"))))))

(deftest test-link-not-found
  (with-redefs [db/get-link #(hash-map :shortlink %2)]
    (let [resp ((mk-routes) (mock/request :get "/_/link?shortlink=a"))]
      (is (= 200 (:status resp)))
      (is (html? resp))
      ;; Default form values for shortlink
      (is (= ["a" "a"]
             (-> resp body-html (input-vals "shortlink"))))
      ;; No default form value for target
      (is (= [nil]
             (-> resp body-html (input-vals "target")))))))

(deftest test-link-found
  (with-redefs [db/get-link #(hash-map :shortlink %2
                                       :target "dest")]
    (let [resp ((mk-routes) (mock/request :get "/_/link?shortlink=a"))]
      (is (= 200 (:status resp)))
      (is (html? resp))
      ;; Default form values for shortlink
      (is (= ["a" "a"]
             (-> resp body-html (input-vals "shortlink"))))
      ;; Default form value for target
      (is (= ["dest"]
             (-> resp body-html (input-vals "target")))))))

(deftest test-invalid-link-update
  (let [setval (atom {})]
    (with-redefs [db/get-link #(hash-map :shortlink %2)
                  db/update-link (fn [_ shortlink target _]
                                   (swap! setval assoc shortlink target)
                                   {:shortlink shortlink :target target}
                                   )]
      (let [resp ((mk-routes) (mock/request :post "/_/link"))]
        (is (= 400 (:status resp)))))))

(deftest test-link-update
  (let [setval (atom {})]
    (with-redefs [db/get-link #(hash-map :shortlink %2)
                  db/update-link (fn [_ shortlink target updated_by]
                                   (swap! setval assoc shortlink [target updated_by])
                                   {:shortlink shortlink :target target :modified? true})]
      (let [resp ((mk-routes) (-> (mock/request :post "/_/link")
                                  (mock/body {:shortlink "a" :target "b"})
                                  (mock/header :x-forwarded-email "dummy@yieldbot.com")))]
        (is (= {"a" ["b" "dummy@yieldbot.com"]} @setval))
        (is (= 201 (:status resp)))
        (is (html? resp))
        ;; No default form values filled
        (is (= [nil nil]
               (-> resp body-html (input-vals "shortlink"))))
        (is (= [nil]
               (-> resp body-html (input-vals "target"))))
        (is (= "http://CANONICAL/a"
               (-> resp
                   body-html
                   (html/select [:#canonical-short-link])
                   first :attrs :value)))))))

(deftest test-setup
  (let [resp ((mk-routes) (mock/request :get "/_/setup.html"))]
    (is (= 200 (:status resp)))
    (is (html? resp))))

(deftest test-search
  (with-redefs [db/search (fn [_ q _]
                            (is (= q "qry"))
                            [{:shortlink "aqry" :target "b" :usage 3}
                             {:shortlink "a" :target "bqry" :usage 2}])]
    (let [resp ((mk-routes) (mock/request :get "/_/search?query=qry"))]
      (is (= 200 (:status resp)))
      (is (html? resp))
      (is (re-find #"<a href=\"http://CANONICAL/_/link\?shortlink=aqry\">" (body-str resp))))))

(deftest test-redir-not-found
  (with-redefs [db/get-link #(hash-map :shortlink %2)]
    (is (= {:status 307 :headers {"Location" "http://CANONICAL/_/link?shortlink=a"} :body ""}
           ((mk-routes) (mock/request :get "/a"))))))

(deftest test-go-redir-not-found
  (with-redefs [db/get-link #(hash-map :shortlink %2)]
    (is (= {:status 307 :headers {"Location" "http://CANONICAL/_/link?shortlink=a"} :body ""}
           (-> ((mk-routes) (mock/request :get "/_/go?link=a"))
               (update :headers dissoc "Content-Type"))))))

(deftest test-redir-found
  (let [link-count (atom {})]
    (with-redefs [db/get-link #(hash-map :shortlink %2 :target "b")
                  db/increment-usage! (fn [_ sl]
                                        (swap! link-count update sl
                                               #(inc (or % 0))))]
      (is (= {:status 307 :headers {"Location" "b"} :body ""}
             ((mk-routes) (mock/request :get "/a"))))
      (is (= {"a" 1} @link-count)))))

(deftest test-go-redir-found
  (let [link-count (atom {})]
    (with-redefs [db/get-link #(hash-map :shortlink %2 :target "b")
                  db/increment-usage! (fn [_ sl]
                                        (swap! link-count update sl
                                               #(inc (or % 0))))]
      (is (= {:status 307 :headers {"Location" "b"} :body ""}
             (-> ((mk-routes) (mock/request :get "/_/go?link=a"))
                 (update :headers dissoc "Content-Type"))))
      (is (= {"a" 1} @link-count)))))

(deftest test-healthz
  (is (= {:status 200
          :body "",
          :headers {"Content-Type" "text/plain; charset=utf-8"}}
         ((mk-routes) (mock/request :get "/_/healthz")))))

(deftest test-not-found
  (is (= 404
         (-> (mock/request :get "/_/not-found")
             ((mk-routes))
             :status))))
