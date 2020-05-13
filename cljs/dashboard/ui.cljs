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


(rum/defc input < rum/reactive [r label minor]
  [:div
   [:input {;:value (rum/react (citrus/subscription r [:game :edit minor]))
           :placeholder label
           :on-change #(citrus/dispatch! r :game :associn [[:edit minor] (.. % -target -value)])}]
   [:br]
   ])

(rum/defc Dashboard < rum/reactive [r]
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
      
     (if (< (:block-number shard-chain-header)
              (:block-number beacon-chain-header))
       [:h4 {:style {:width "100%"}} "Node is syncing.."]
       [:h4 {:style {:width "100%"}} "Node synced."]
       )
      
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


(defn login-view []
  [:login-screen
   [:div
    [:img {:src "/images/logo.png"}]
    [:password
     [:input {:placeholder "Password"}]
     [:login-btn [:img {:src "/images/arrow.svg"}]]]]])

(defn app []
  [:div
   [:header
    [:img {:src "/images/header-logo.png"}]
    [:div
     [:div
      [:p "Address:"]
      [:a (@state :address)]]
     (if (:comitee @state)
    [:div
     [:dot]
     [:p "Currently elected"]]
    [:div
     [:dot.orange]
     [:p "Waiting to be selected"]])
     ]]

   [:top-data
    [:div.first
     [:p "Balance"]
     [:value (get-in @state [:top-data :balance]) [:span "ONE"]]]
    [:div
     [:p "Expected Return"]
     [:value (get-in @state [:top-data :return]) [:span "%"]]]
    [:div
     [:p "Uptime (AVG)"]
     [:value (get-in @state [:top-data :uptime]) [:span "%"]]]
    [:div
     [:p "Lifetime Rewards"]
     [:value (get-in @state [:top-data :rewards]) [:span "ONE"]]]
    [:div.last
     [:p "Delegators"]
     [:value (get-in @state [:top-data :delegators-head]) [:span "HEAD"]]]]

   [:info-titles
    [:p "General info"]
    [:p "Delegators"]]
   [:info
    [:general
     [:card
      [:div
       [:input {:value (get-in @state [:general :name])}]
       [:label "Validator Name"]]
      [:columns
       [:column
        [:div
         [:input {:value (get-in @state [:general :desc])}]
         [:label "Description"]]
        [:div
         [:input {:value (get-in @state [:general :details])}]
         [:label "Details"]]
        [:div
         [:input {:value (get-in @state [:general :comission])}]
         [:label "Comission Rate"]]]
       [:column
        [:div
         [:input {:value (get-in @state [:general :site])}]
         [:label "Website"]]
        [:div
         [:input {:value (get-in @state [:general :contact])}]
         [:label "Security Contact"]]
        [:div
         [:input {:value (get-in @state [:general :max-del])}]
         [:label "Max Total Delegation"]]]]
      [:btn-wrapper
       [:save-btn "Save Changes"]]]]

    [:delegators
     [:card
      [:head-row
       [:number [:p "No."]]
       [:addres [:p "Address"]]
       [:amount [:p "Amount"]]
       [:reward [:p "Reward"]]]
      (for [delegator (map-indexed vector (@state :delegators))]
        [:row {:class (when (even? (first delegator)) "light-bg")}
         [:number [:p (inc (first delegator))]]
         [:addres [:a ((second delegator) :address)]]
         [:amount [:p ((second delegator) :amount)]]
         [:reward [:p ((second delegator) :reward)]]])]
     [:stake-data
      [:div.first
       [:p "Total Staked"]
       [:value (get-in @state [:stake :total]) [:span "ONE"]]]
      [:div
       [:p "Delegated"]
       [:value (get-in @state [:stake :delegated] [:span "ONE"])]]
      [:div.last
       [:p "Self Stake"]
       [:value (get-in @state [:stake :self] [:span "ONE"])]]]]]

   [:footer
    [:div
     [:p "Don't forget to donate some ONE or ETH to the ZGEN DAO to support our further work:"]
     [:a "0xAAA77711c7b70e20d32Ec50b21Df89e742607b9b"]]
    [:div
     [:p "Send your feature requests to:"]
     [:a "crypto@zgen.hu"]]
    [:div
     [:p "Source:"]
     [:a {:href "google.com"} "liszper/one-validator-dashboard"]]]])
