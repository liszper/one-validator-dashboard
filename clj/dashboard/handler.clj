(ns dashboard.handler
  (:require
   [cheshire.core :refer :all]
   [dashboard.commands :refer :all]
   [clojure.java.io :as io]
   [system.repl :refer [system]]
   [hara.io.scheduler :as hara]
   [hara.time :as t]
   [clojure.data.json :as json]

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


(defmethod event-msg-handler :validator/create [{:keys [uid ?data]}]
  (let [
        response (create-validator ?data)
        response (or (when (not= (:err response) "") (:err response)) (:out response))
        ]
    (send-all! :data/latest-response (str response))
  ))

(defmethod event-msg-handler :validator/update [{:keys [uid ?data]}]
  (let [
        response (update-validator ?data)
        response (or (when (not= (:err response) "") (:err response)) (:out response))
        ]
    (send-all! :data/latest-response (str response))
    (println (str ?data))
  ))


(def scheduler 
  (hara/scheduler 
    {
     :tick {:handler (fn [t] 
                       (let [latest-headers (get-latest-headers)]
                         (send-all! :data/latest-headers latest-headers)
                         )) :schedule "/1 * * * * * *"}

     :tack {:handler (fn [t] 
                       (let [validator-info (get-validator-info)]
                         (send-all! :data/validator-info validator-info)
                         )) :schedule "/2 * * * * * *"}

     :tock {:handler (fn [t]
                       (let [balances (get-wallet-balances)]
                         (send-all! :data/wallet-balances balances)
                         (when-let [backup (:backup @state)]
                           (send-all! :data/wallet-backup backup))
                         )) :schedule "/3 * * * * * *"}
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
