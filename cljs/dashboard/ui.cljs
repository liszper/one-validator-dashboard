(ns dashboard.ui
  (:require 
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
    {}))

(defmethod game :update [_ new-state state]
  (let [state (merge state (first new-state))
        local-storage {:method :set :data state :key :game}]
    (map-of state local-storage)))

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


(rum/defc input < rum/reactive [r major minor function]
  [:input {:value (rum/react (citrus/subscription r [major minor]))
           :on-change #(citrus/dispatch! r major function {minor (.. % -target -value)})}])


(rum/defc World < rum/reactive [r]
  (let [
        {:keys [latest-headers addresses validator-info]} (rum/react (citrus/subscription r [:game]))
        {:keys [beacon-chain-header shard-chain-header]} latest-headers
        ]
    [:div {:style {:margin "30px" :padding "30px" :border "3px solid"}}
     [:h2 {:style {:text-align "center" :width "100%"}} "Validator Dashboard of "(:name (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Address: "(:address (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Status: "(:epos-status validator-info)]
     [:h3 {:style {:text-align "center" :width "100%"}} "In committee? "(str (:currently-in-committee validator-info))]
     [:div {:style {:display "flex" :justify-content "center" :align-items "center" :flex-wrap "wrap"}}
      [:div {:style {:margin "30px" :padding "30px" :border "3px solid rgba(0,0,0,0.3)"}}
      [:h4 "Beacon:"]
      [:h5 "Block: "(:block-number beacon-chain-header)]
      [:h5 "Epoch: "(:epoch beacon-chain-header)]
      [:h5 "Shard ID: "(:shard-id beacon-chain-header)]]
     [:div {:style {:margin "30px" :padding "30px" :border "3px solid rgba(0,0,0,0.3)"}}
      [:h4 "Shard:"]
      [:h5 "Block: "(:block-number shard-chain-header)]
      [:h5 "Epoch: "(:epoch shard-chain-header)]
      [:h5 "Shard ID: "(:shard-id shard-chain-header)]]
     [:div {:style {:width "100%"}}
      [:h3 "Changing validator information is coming soon!"]
      [:h3 "Work in progress.."]
      (str validator-info)
      ]
     ]]))

(rum/mount (World reconciler)
           (. js/document (getElementById "ui")))



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



