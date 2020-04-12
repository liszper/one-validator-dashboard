(ns dashboard.handler
  (:require
   [cheshire.core :refer :all]
   [clojure.java.io :as io]
   [system.repl :refer [system]]
   [hara.io.scheduler :as hara]
   [hara.time :as t]

   [compojure.core :refer [defroutes GET POST]]
   [compojure.route :as route :refer [resources]]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.util.response :refer [response content-type resource-response]]
  
   [taoensso.sente :as sente]))

(defn drop-nth [n coll] (keep-indexed #(if (not= %1 n) %2) coll))

(defmulti event-msg-handler :id)

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
     (event-msg-handler ev-msg))

(defmethod event-msg-handler :default ; Fallback
[{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
(let [session (:session ring-req)
      uid     (:uid     session)]
 (println (str "Unhandled event: " event))
 (when ?reply-fn
      (?reply-fn {:umatched-event-as-echoed-from-from-server event}))))

(defmethod event-msg-handler :chsk/ws-ping
  [ev-msg] (let [uid (:uid ev-msg)]
             ))
(defn all-connected-uids [] (:any @(:connected-uids (:sente system))))

(defn send! [uid path content] (when-let [sfn (:chsk-send! (:sente system))] (sfn uid [path content])))
(defn send-all! [path content] (doseq [uid (all-connected-uids)] (send! uid path content)))

(defmethod event-msg-handler :chsk/uidport-open [ev-msg] (let [uid (:uid ev-msg)] nil))

(defmethod event-msg-handler :chsk/uidport-close [ev-msg] (let [uid (:uid ev-msg)] nil))

(defmethod event-msg-handler :player/state [{:keys [uid ?data]}] nil)

(defmethod event-msg-handler :game/action [{:keys [uid ?data]}] nil)

(defmethod event-msg-handler :game/report [{:keys [uid ?data]}] nil)

(def scheduler 
  (hara/scheduler 
    {
     :tick {:handler (fn [t] (println "Test" t)) :schedule "/1 * * * * * *"}
     }
    {}
    {:clock {:type "clojure.lang.PersistentArrayMap"
             :timezone "CET"
             :interval 1
             :truncate :millisecond}}))

(defroutes routes
  (resources "/js" {:root "js"})
  (resources "/css" {:root "css"})
  (GET "/" [] (-> (resource-response "index.html")
                  (content-type "text/html")))

  (GET  "/ws/network"  req ((:ring-ajax-get-or-ws-handshake (:sente system)) req))
  (POST "/ws/network"  req ((:ring-ajax-post (:sente system)) req))
  
  (route/not-found "Not Found"))

(def middleware (-> site-defaults
                 (assoc-in [:security :anti-forgery] false)))

(def app
  (-> routes
      ring.middleware.keyword-params/wrap-keyword-params
      (wrap-defaults middleware)))
