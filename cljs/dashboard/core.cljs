(ns dashboard.core
  (:require 
    [dashboard.ui :refer [Dashboard]]
    [cljs.reader :as reader]
    [clojure.core.async :as async]
    [potpuri.core :refer [map-of]]
    [rum.core :as rum]
    [citrus.core :as citrus]
    [chronoid.core :as c]
    [taoensso.sente :as sente]
    [brave.swords :as x]
    [cljsjs.howler]
    [mantra.core :as m]))


(defn deep-merge [v & vs]
  (letfn [(rec-merge [v1 v2]
            (if (and (map? v1) (map? v2))
              (merge-with deep-merge v1 v2)
              v2))]
    (when (some identity vs)
      (reduce #(rec-merge %1 %2) v vs))))

(defn console! [& s] (js/console.log (apply str s)))

;//////\\\\\\\\ CONTROLLERS

(defmulti game (fn [event] event))

(defmethod game :init []
  {:local-storage
   {:method :get
    :key :game
    :on-read :init-ready}})

(defmethod game :init-ready [_ [state]]
  (if-not (nil? state)
    (map-of state)
    {:address "one1x8kgyyqgslzrm7jrlj4m7rpg285etdywjk7gez"
     :comitee true
     :top-data {:balance "120" :return "20.29" :uptime "98.56" :rewards "60,49" :delegators-head "17"}
     :general {:name "Zgen Validator" :desc "Little this little that" :site "zgen.hu" :details "idk" :contact "crypto@zgen.hu" :comission "15.15%" :max-del "100,000,000"}
     :delegators [{:address "one1x8kgyyqgslzrm7jrlj4m7rpg285etdywjk7gez" :amount "2,450,000" :reward "34.533"}
                  {:address "one1vyd0rgpspyjme3ufrvlmk8mza8ez929ngtamlj" :amount "1,000,000" :reward "21.89"}
                  {:address "one1uq5fys06g3rvmrxj2q0acd9e2fzjd54ctjj9xj" :amount "100,000" :reward "43.23"}
                  {:address "one1mpzx5wr2kmz9nvkhsgj6jr6zs87ahm0gxmhlck" :amount "20,000" :reward "2.33"}
                  {:address "one159h80syujml77x03mgd3p4ehck6pderfnazrkv" :amount "20,000" :reward "4.67"}
                  {:address "one1qtx776yews0t80k9ppe6d2qd76clnpp2tjs8wt" :amount "10,000" :reward "5.21"}
                  {:address "one1ku37y4dhlzjmejjc5mghtfts988tp0qxfhu2hq" :amount "10,000" :reward "9.99"}
                  {:address "one159h80syujml77x03mgd3p4ehck6pderfnazrkv" :amount "20,000" :reward "4.67"}
                  {:address "one1qtx776yews0t80k9ppe6d2qd76clnpp2tjs8wt" :amount "10,000" :reward "5.21"}
                  {:address "one1ku37y4dhlzjmejjc5mghtfts988tp0qxfhu2hq" :amount "10,000" :reward "9.99"}
                  {:address "one159h80syujml77x03mgd3p4ehck6pderfnazrkv" :amount "20,000" :reward "4.67"}
                  {:address "one1qtx776yews0t80k9ppe6d2qd76clnpp2tjs8wt" :amount "10,000" :reward "5.21"}
                  {:address "one1ku37y4dhlzjmejjc5mghtfts988tp0qxfhu2hq" :amount "10,000" :reward "9.99"}]
     :stake {:total "49,750,520" :delegated "47,300,520" :self "2,450,000"}}
    ))

(defmethod game :update [_ new-state state]
  (let [state (merge state (first new-state))
        ;local-storage {:method :set :data state :key :game}
        ]
    (map-of state 
            ;local-storage
            )))

(defmethod game :associn [_ new-state state]
  (let [state (assoc-in state (first (first new-state)) (second (first new-state)))
        ;local-storage {:method :set :data state :key :game}
        ]
    (map-of state 
            ;local-storage
            )))

(defmulti party (fn [event] event))

(defmethod party :init []
  {:local-storage
   {:method :get
    :key :party
    :on-read :init-ready}})

(defmethod party :init-ready [_ [state]]
  (if-not (nil? state)
    (map-of state)
    {:state {}}))

(defmethod party :update [_ new-state state]
  (let [state (first new-state)
        local-storage {:method :set :data state :key :party}]
    (map-of state local-storage)))


;//////\\\\\\\\ SYSTEM

  (let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/ws/network" {:type :auto})]
    (def chsk       chsk)
    (def ch-chsk    ch-recv)
    (def chsk-send! send-fn)
    (def chsk-state state))

(defn cloud-storage [reconciler controller-name effect]
  (let [{:keys [method data key on-read]} effect]
    (case method
      :set (chsk-send! [:data/save (map-of key data)])
      :get (chsk-send! [:data/load key] 1000 (fn [data] (citrus/dispatch! reconciler controller-name on-read data)))
      nil)))

(defn local-storage [reconciler controller-name effect]
  (let [{:keys [method data key on-read]} effect]
    (case method
      :set (js/localStorage.setItem (name key) data)
      :get (->> (js/localStorage.getItem (name key))
                (cljs.reader/read-string)
                (citrus/dispatch! reconciler controller-name on-read))
      nil)))

(defonce reconciler
  (citrus/reconciler
    {:state (atom {})
     :controllers (map-of game party)
     :effect-handlers (map-of cloud-storage local-storage)}))

(defonce init-ctrl (citrus/broadcast-sync! reconciler :init))


;//////////\\\\\\\\\ UI

(rum/mount (Dashboard reconciler) (. js/document (getElementById "app")))


(defmulti event-msg-handler :id)
(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}] (console! "Unhandled event: " event))
(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (console! "First opened connection.")))
(defmethod event-msg-handler :chsk/ping
  [{:as ev-msg :keys [?data]}]
  (console! "Server ping."))
(defmethod event-msg-handler :chsk/ws-ping
  [{:as ev-msg :keys [?data uid]}] 
  (let [[?uid ?csrf-token ?handshake-data] ?data] 
    (console! "Websocket ping: " ?uid)))

(def router_ (atom nil))
(defn stop-router! [] (when-let [stop-f @router_] (do (stop-f) (console! "Router stopped."))))
(defn start-router! []
  (do
    (when @router_ (stop-router!)) 
    (reset! router_ (sente/start-chsk-router! ch-chsk event-msg-handler))
    (console! "Router started.")))

(start-router!)



(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data uid]}] 
  (let [[?uid ?csrf-token ?handshake-data] ?data] 
    (console! "Connected.")
    ))


(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}] 
  (let [[event-key event-data] ?data]
    (citrus/dispatch! reconciler :game :update {(keyword (name event-key)) event-data})
    ))



