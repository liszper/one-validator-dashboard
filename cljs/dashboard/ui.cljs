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
    {}))

(defmethod game :update [_ new-state state]
  (let [state (merge state (first new-state))
        local-storage {:method :set :data state :key :game}]
    (map-of state local-storage)))

(defmethod game :associn [_ new-state state]
  (let [state (assoc-in state (first (first new-state)) (second (first new-state)))
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


(rum/defc input < rum/reactive [r label minor]
  [:div
   [:input {;:value (rum/react (citrus/subscription r [:game :edit minor]))
           :placeholder label
           :on-change #(citrus/dispatch! r :game :associn [[:edit minor] (.. % -target -value)])}]
   [:br]
   ])


(rum/defc World < rum/reactive [r]
  (let [
        {:keys [latest-headers latest-response addresses validator-info edit wallet-backup wallet-balances]} (rum/react (citrus/subscription r [:game]))
        {:keys [beacon-chain-header shard-chain-header]} latest-headers
        fund (reduce (fn [x y] (+ x (get y "amount"))) 0 (-> wallet-balances first second))
        ready? (< 0 fund)
        ]
 
    [:div {:style {:margin "30px" :padding "30px" :max-width "100%" :overflow "hidden" :width "100%" :border "3px solid"}}
     (when wallet-backup [:h3 {:style {:text-align "center" :width "100%"}} wallet-backup])
     (when wallet-balances [:h3 {:style {:text-align "center" :width "100%"}} "Balances: " [:br](str wallet-balances)])
     (if ready?
       [:h4 "Total balance: "fund]
       [:h4 "You don't have any ONE yet, "[:a {:href "https://faucet.os.hmny.io/" :target "_blank"} "the faucet first."]])
     (when validator-info
       [:div {:style {:margin "30px" :padding "30px" :max-width "100%" :overflow "hidden" :width "100%" :border "3px solid"}}
     [:h2 {:style {:text-align "center" :width "100%"}} "Validator Dashboard of "(:name (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Validator Address: "(:address (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Status: "(:epos-status validator-info)]
     [:h3 {:style {:text-align "center" :width "100%"}} "In committee? "(str (:currently-in-committee validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Delegators:"]
     [:ul
      (map (fn [x]
            [:li {:style {:margin "30px" :padding "30px" :border "3px solid rgba(0,0,0,0.3)"}}
             [:h3 (str "Address: " (:delegator-address x))]
             [:h3 (str "Amount: " (:amount x))]
             [:h3 (str "Reward: " (:reward x))]
             ]
            )
          (:delegations (:validator validator-info)))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Contact: "(:security-contact (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Website: "(:website (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Details: "(:details (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Creation: "(:creation-height (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Commission rate: "(:rate (:validator validator-info))]
     [:h3 {:style {:text-align "center" :width "100%"}} "Max delegation: "(:max-total-delegation (:validator validator-info))]
     ])
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
     (when-not validator-info
       [:h1 {:style {:width "100%"}} "This node it is not a validator yet."])
     (when ready?
       [:div {:style {:width "100%"}}
      [:h4 (if validator-info "Edit validator info:" "Let's create it:")]
      (str edit)
      [:br]
      (input r "Validator name" :v-name)
      (input r "Validator identity" :v-identity)
      (input r "Website" :v-website)
      (input r "Details" :v-details)
      (input r "Security contact" :v-contact)
      (input r "Commission rate" :v-commission)
      (input r "Max total delegation" :v-total)
      (if validator-info
        [:button.btn
       {:on-click
        #(do
          (chsk-send! [:validator/update edit])
          (citrus/dispatch! reconciler :game :update {:edit {}})
          )}
       "Update"]
      [:button.btn
       {:on-click
        #(do
          (chsk-send! [:validator/create edit])
          (citrus/dispatch! reconciler :game :update {:edit {}})
          )}
       "Create"])
      (when latest-response [:p "Latest response:"])
      [:p (str latest-response)]

      ])
     (when validator-info
     [:div
      [:h2 {:style {:width "100%" :text-align "center" :font-weight "600" :margin-top "30px"}} "Don't forget to donate some ONE or ETH to the ZGEN DAO to support our further work: "[:a {:href "https://etherscan.io/address/0xAAA77711c7b70e20d32Ec50b21Df89e742607b9b" :target "_blank"} "0xAAA77711c7b70e20d32Ec50b21Df89e742607b9b"]]
      [:h2 {:style {:width "100%" :text-align "center" :font-weight "600" :margin-top "30px"}} "Send your feature requests to: crypto@zgen.hu"]
      [:h2 {:style {:font-weight "600" :margin-top "30px"}} "Source: "[:a {:href "https://github.com/liszper/one-validator-dashboard" :target "_blank"}"liszper/one-validator-dashboard"]]
     ])]]

))

(rum/mount (World reconciler)
           (. js/document (getElementById "app")))



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



