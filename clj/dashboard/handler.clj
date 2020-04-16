(ns dashboard.handler
  (:require
   [cheshire.core :refer :all]
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

(defn download-cli [] (clojure.java.shell/sh "curl" "-LO" "https://harmony.one/hmycli" "&&" "mv" "hmycli" "hmy" "&&" "chmod" "+x" "hmy"))

(defn generate-bls [] (:out (clojure.java.shell/sh "../hmy" "keys" "generate-bls-key")))

(defn download-node [] (clojure.java.shell/sh "curl" "-LO" "https://raw.githubusercontent.com/harmony-one/harmony/master/scripts/node.sh" "&&" "chmod" "a+x" "node.sh"))

(defn run-node [] (clojure.java.shell/sh "tmux" "new-session" "-d" "-s" "nodeSession" "./run-node.sh"))

(defn create-wallet [wallet-name] (:out (clojure.java.shell/sh "../hmy" "keys" "add" wallet-name)))

(defn wallet-balance [address]
    (let [
               result (:out (clojure.java.shell/sh "../hmy" "balances" address))
               ]
      (when-not (:err result)
        (json/read-str
      result))))

(defn list-wallets []
  (dissoc
    (into 
    (hash-map)
    (map
    (fn [x]
      (let [x (clojure.string/trim x)
            [k v] (clojure.string/split x #"\t")
            ]
        [k v]))
    (clojure.string/split
      (subs 
        (:out (clojure.java.shell/sh "../hmy" "keys" "list"))
        51)
      #"\n")))
    ""))

(def backup-atom (atom nil))

(when (empty? (list-wallets))
  (let [backup (create-wallet "autowallet")]
    (reset! backup-atom backup)))

(def wallet (-> (list-wallets) first second))

(defn create-validator [{:keys [v-name
                                v-identity
                                v-website
                                v-details
                                v-contact
                                v-commission
                                v-total]}]
  (let [bls (subs (:out (clojure.java.shell/sh "./get-bls.sh")) 0 96)]
    (println "BLS:" bls)
      (clojure.java.shell/sh 
             "../hmy"
              "--node=https://api.s0.os.hmny.io"
              "staking"
              "create-validator"
              "--validator-addr" wallet
              "--bls-pubkeys" bls
              "--bls-pubkeys-dir" "/root"
              ;"--passphrase-file" (str "../"bls".pass")
             "--name" (if v-name v-name (str "Autogenerate validator" (rand-int 40000)))
             "--identity" (if v-identity v-identity "Identity")
             "--website" (if v-website v-website "Website")
             "--details" (if v-details v-details "Details")
             "--security-contact" (if v-contact v-contact "Contact")
             "--rate" (str (if v-commission v-commission 0.1))
             "--max-rate" "1"
             "--max-change-rate" "1"
             "--max-total-delegation" (str (if v-total v-total 100000000))
             "--min-self-delegation" "10000"
             "--amount" "10000"
             ) 
             ))

(defn update-validator [
                          {:keys [v-name
                v-identity
                v-website
                v-details
                v-contact
                v-commission
                v-total
                ]}
                          ]
      (apply clojure.java.shell/sh 
           (concat
             ["../hmy"
              "--node=https://api.s0.os.hmny.io"
              "staking" "edit-validator" "--validator-addr" wallet]
             (when v-name ["--name" v-name]) 
             (when v-identity ["--identity" v-identity]) 
             (when v-website ["--website" v-website])
             (when v-details ["--details" v-details]) 
             (when v-contact ["--security-contact" v-contact]) 
             (when v-commission ["--rate" v-commission]) 
             (when v-total ["--max-total-delegation" v-total]) 
             ))
  )

(defn get-validator-info []
  (let [
        response (clojure.java.shell/sh
                                       "../hmy"
                                       "--node=https://api.s0.os.hmny.io"
                                       "blockchain" "validator"
                                       "information" wallet)
        ]
    (when (or (= (:err response) "") (nil? (:err response)))
    (:result
      (json/read-str
        (:out response)
        :key-fn keyword
        )))))

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

(def state (atom {}))

(def scheduler 
  (hara/scheduler 
    {
     :tick {:handler (fn [t] 
                       (let [latest-headers 
                             (:result
                               (json/read-str
                               (:out (clojure.java.shell/sh "../hmy" "blockchain" "latest-headers"))
                               :key-fn keyword
                               ))
                             ]
                         (send-all! :data/latest-headers latest-headers)
                         )) :schedule "/1 * * * * * *"}

     :tack {:handler (fn [t] 
                       (let [
                             validator-info (get-validator-info)
                             ]
                         (send-all! :data/validator-info validator-info)
                         )) :schedule "/2 * * * * * *"}

     :tock {:handler
            
            (fn [t]
         
              (let [
                    wallets (list-wallets)
                    balances (into (hash-map) (map (fn [[_ wallet]] [wallet (wallet-balance wallet)]) wallets))
                    ]
                (send-all! :data/wallet-balances balances)
                (send-all! :data/wallet-backup @backup-atom)
                )
      
              ) :schedule "/3 * * * * * *"}
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
