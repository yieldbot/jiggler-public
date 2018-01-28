;; Copyright (c) 2017 Yieldbot
(ns jiggler.routes
  (:require
   [aleph.http :as http]
   [aleph.netty]
   [clojure.tools.logging :as log]
   [com.stuartsierra.component :as component]
   [compojure.core :as cc]
   [compojure.route :as route]
   [jiggler.db :as db]
   [net.cgrand.enlive-html :as html]
   [ring.logger :as logger]
   [ring.middleware.defaults :as ring-defaults]
   [ring.middleware.not-modified :refer [wrap-not-modified]])
  (:import
   [org.apache.http.client.utils URIBuilder])
  (:gen-class))

(def disallowed-patterns
  [;; No empty links
   #"^$"
   ;; A few special characters are reserved (especially "_")
   #"^_$"
   #"^-$"
   #"^\.$"
   ;; No weird characters to confuse the URL parser
   #"[#?&/\\]"
   ;; Nothing with spaces
   #"\s"
   ;; The "link" link is special
   #"^link$"])

(defn disallowed? [shortlink]
  (some #(re-find % shortlink) disallowed-patterns))

(defn update-attr
  [attr f & args]
  #(apply update-in % [:attrs attr] f args))

(html/defsnippet head-snippet "templates/head.html" [:head]
  [base-url title]
  [:title] (html/content title)
  [:link] (update-attr :href #(str base-url "_/" %)))

(html/defsnippet navbar-snippet "templates/navbar.html" [:#navbar]
  [base-url curpage]
  [:a] (update-attr :href #(str base-url %))
  [:#homelink]   (if (= curpage :link)   (html/add-class "curpage") identity)
  [:#searchlink] (if (= curpage :search) (html/add-class "curpage") identity)
  [:#helplink]   (if (= curpage :help)   (html/add-class "curpage") identity))

(html/deftemplate shortlink-template "templates/link.html"
  [base-url {:keys [shortlink]}]
  [:head] (html/substitute (head-snippet base-url
                                         (str "Jiggler Short Links"
                                              (if shortlink (str ": " shortlink)))))
  [:#navbar] (html/substitute (navbar-snippet base-url :link))
  [(html/attr= :name "shortlink")] (html/do->
                                    (if shortlink
                                      (html/set-attr :value shortlink)
                                      identity)
                                    (html/set-attr :autofocus "autofocus"))
  [:.target] (html/add-class "hidden")
  [:#confirmation] false)

(html/deftemplate target-template "templates/link.html"
  [base-url {:keys [shortlink target]}]
  [:head] (html/substitute (head-snippet base-url (str "Jiggler Short Links: " shortlink)))
  [:#navbar] (html/substitute (navbar-snippet base-url :link))
  [(html/attr= :name "shortlink")] (html/set-attr :value shortlink)
  [:.shortlink] (html/add-class "disabled")
  [:.shortlink :input] (html/set-attr :disabled true)
  [:.shortlink :button] (html/set-attr :disabled true)
  [(html/attr= :name "target")] (html/do->
                                 (if target
                                   (html/set-attr :value target)
                                   identity)
                                 (html/set-attr :autofocus "autofocus"))
  [:#confirmation] false)

(defn rand-link []
  (let [characters (->> (concat (range (int \a) (inc (int \z)))
                                (range (int \0) (inc (int \9))))
                        (map char)
                        (remove #{\a \e \i \o \u}))]
    (->> #(rand-nth characters)
         (repeatedly 12)
         (apply str "R:"))))

(defn link-follow-url
  "The URL that follows a shortlink."
  [base-url shortlink]
  (str base-url shortlink))

(defn link-edit-url
  "The URL at which you edit a shortlink."
  [base-url shortlink]
  (-> (str base-url "_/link")
      URIBuilder.
      (.addParameter "shortlink" shortlink)
      str))

(html/defsnippet confirmation-snippet "templates/confirmation.html" [:#confirmation]
  [base-url {:keys [shortlink target]}]
  [:#canonical-short-link] (html/set-attr :value (link-follow-url base-url shortlink))
  [:#target] (html/do-> (html/set-attr :href target)
                        (html/content target)))

(html/deftemplate modified-template "templates/link.html"
  [base-url link]
  [:head] (html/substitute (head-snippet base-url "Jiggler Short Links"))
  [:#navbar] (html/substitute (navbar-snippet base-url :link))
  [(html/attr= :name "shortlink")] (html/set-attr :autofocus "autofocus")
  [:.target] (html/add-class "hidden")
  [:#confirmation] (html/substitute (confirmation-snippet base-url link)))

(defn link-template [base-url {:keys [shortlink modified? action] :as link}]
  ((cond
    modified? modified-template
    (and shortlink (not (disallowed? shortlink))) target-template
    :else shortlink-template)
   base-url
   link))

(html/deftemplate help-template "templates/help.html" [base-url]
  [:head] (html/substitute (head-snippet base-url "Jiggler Short Links Help"))
  [:#navbar] (html/substitute (navbar-snippet base-url :help))
  [:span.selflink] (html/substitute base-url)
  [:a.selflink] (update-attr :href #(str base-url %)))

(html/deftemplate setup-template "templates/setup.html" [base-url]
  [:head] (html/substitute (head-snippet base-url "Jiggler Short Links Help"))
  [:#navbar] (html/substitute (navbar-snippet base-url :help))
  [:form.selflink] (update-attr :action #(str base-url %)))

(html/defsnippet search-result-snippet "templates/search-results.html" [:.search-result]
  [base-url {:keys [shortlink target usage]}]
  [:.search-shortlink :a] (html/do-> (html/set-attr :href (link-edit-url base-url shortlink))
                                     (html/content shortlink))
  [:.search-usage] (html/content (str usage))
  [:.search-target :a] (html/do-> (html/set-attr :href target)
                                  (html/content target)))

(html/deftemplate search-template "templates/search-results.html"
  [base-url query links]
  [:head] (html/substitute (head-snippet base-url "Jiggler Short Links Search"))
  [:#navbar] (html/substitute (navbar-snippet base-url :search))
  [(html/attr= :name "query")] (html/do->
                                (html/set-attr :autofocus "autofocus")
                                (html/set-attr :value query))
  [:.search-result] (html/clone-for [link links]
                                    (html/substitute
                                     (search-result-snippet base-url link))))


(html/deftemplate search-provider-template
  {:parser html/xml-parser} "templates/search.xml"
  [base-url]
  [:Url] (update-attr :template #(str base-url %))
  [:moz:SearchForm] (html/content base-url))

(defn html-resp [status body]
  {:status status
   :headers {"Content-Type" "text/html"}
   :body body})

(defn opensearch-resp [status body]
  {:status status
   :headers {"Content-Type" "application/opensearchdescription+xml"}
   :body body})

(defn redir-resp [target]
  {:status 307
   :headers {"Location" target}})

(defn redir
  "Look up the shortlink in the database and make a redirection response."
  [database base-url shortlink more query-string]
  (let [{:keys [target] :as link} (db/get-link database shortlink)]
    (if target
      (do
        (db/increment-usage! database shortlink)
        (redir-resp (str target more
                         (if query-string (str "?" query-string) ""))))
      (redir-resp (link-edit-url base-url shortlink)))))

(defn healthz []
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body ""})

(defn ui-routes [database base-url]
  (cc/routes
   (cc/GET "/" [] (redir-resp (str base-url "_/link")))
   (cc/context "/_" []
     (cc/GET "/" [] (redir-resp (str base-url "_/link")))
     (cc/GET "/link" [shortlink action]
       (->> (if (= action "random") (rand-link) shortlink)
            (db/get-link database)
            (link-template base-url)
            (html-resp 200)))
     (cc/POST "/link" {{:keys [shortlink target]} :params
                       {:strs [x-forwarded-email]} :headers}
       (cond
        (not (and shortlink target)) {:status 400 :body "Missing parameters"}
        (disallowed? shortlink) {:status 401
                                 :body "Forbidden"}
        :else (->> (db/update-link database shortlink target x-forwarded-email)
                   (link-template base-url)
                   (html-resp 201))))
     (cc/GET "/go" {{:keys [link]} :params}
       (let [[_ shortlink more query-string]
             (re-matches #"([^/?]*)(/[^?]*)?(?:\?(.*))?" link)]
         (redir database base-url shortlink more query-string)))
     (cc/GET "/help" [] (html-resp 200 (help-template base-url)))
     (cc/GET "/setup.html" [] (html-resp 200 (setup-template base-url)))
     (cc/GET "/search" [query]
       (html-resp 200 (search-template base-url query (db/search database query 30))))
     (cc/GET "/healthz" []
       (healthz))
     (cc/GET "/search.xml" []
       (opensearch-resp 200 (search-provider-template base-url)))
     (wrap-not-modified (route/resources "/"))
     (route/not-found "Not found"))))

(defn redir-route [database base-url]
  (cc/GET "/:shortlink{[^/?]*}:more{.*}" req
    (let [{:keys [query-string params]} req
          {:keys [shortlink more]} params]
      (redir database base-url shortlink more query-string))))

(defn jiggler-routes [database base-url]
  (cc/routes
    (-> (ui-routes database base-url)
        (ring-defaults/wrap-defaults
         ;; NOTE(lbarrett, 2016-11-09): Set proxy to false to avoid an
         ;; assertion in ring-ssl/wrap-forwarded-scheme. I don't believe the
         ;; proxy wrappers are really doing anything for us anyway.
        (-> ring-defaults/api-defaults
            (assoc :proxy false)
            (assoc-in [:responses :absolute-redirects] false))))
    ;; Hardcode the "link" link.
    (cc/GET "/link" []
      (redir-resp base-url))
    (redir-route database base-url)
    (route/not-found "Not found")))

(defrecord Routes [base-url database routes]
  component/Lifecycle
  (start [component]
    (assoc component :routes (jiggler-routes database base-url)))
  (stop [component]
    (dissoc component :routes)))

(defn make-routes [base-url]
  (map->Routes {:base-url base-url}))

(defrecord Webserver [routes port server]
  component/Lifecycle
  (start [component]
    (let [server (-> (:routes routes)
                     (logger/wrap-with-logger {:printer :no-color})
                     (http/start-server {:port port}))]
    (log/infof "Started serving on port %s" port)
    (assoc component :server server)))
  (stop [component]
    (.close server)
    (dissoc component :server)))

(defn make-webserver [port]
  (map->Webserver {:port port}))

(defn wait-for-webserver [webserver]
  (-> webserver :server aleph.netty/wait-for-close))
