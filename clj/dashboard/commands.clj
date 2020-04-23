(ns dashboard.commands
  (:require
   [clojure.java.io :as io]
   [clojure.data.json :as json]
   ))

(defn download-cli [] 
  (clojure.java.shell/sh 
    "curl" "-LO" "https://harmony.one/hmycli" "&&" "mv" "hmycli" "hmy" "&&" "chmod" "+x" "hmy"))

(defn generate-bls [] 
  (clojure.java.shell/sh
    "../hmy" "keys" "generate-bls-key"))

(defn download-node [] 
  (clojure.java.shell/sh 
    "curl" "-LO" "https://raw.githubusercontent.com/harmony-one/harmony/master/scripts/node.sh"
    "&&"
    "chmod" "a+x" "node.sh"))

(defn run-node [] 
  (clojure.java.shell/sh
    "tmux" "new-session" "-d" "-s" "nodeSession" "./run-node.sh"))

(defn create-wallet
  [wallet-name]
  (:out
    (clojure.java.shell/sh
      "../hmy" "keys" "add" wallet-name)))

(defn wallet-balance 
  [address]
  (let [result (:out (clojure.java.shell/sh "../hmy" "balances" address))]
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

(def wallet (-> (list-wallets) first second))

(def bls (subs (:out (clojure.java.shell/sh "./get-bls.sh")) 0 96))

(defn create-validator 
  [{:keys
    [v-name
     v-identity
     v-website
     v-details
     v-contact
     v-commission
     v-total]}]
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
    "--amount" "10000"))


(defn update-validator 
  [{:keys
    [v-name
     v-identity
     v-website
     v-details
     v-contact
     v-commission
     v-total
     ]}]
  (apply 
    clojure.java.shell/sh 
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
      )))

(def state (atom {}))

(when (empty? (list-wallets))
  (let [backup (create-wallet "autowallet")]
    (swap! state assoc :backup backup)))

(defn delegate-validator
  [{:keys [amount]}]
  (clojure.java.shell/sh 
    "../hmy"
    "--node=https://api.s0.os.hmny.io"
    "staking"
    "delegate"
    "--delegator-addr" wallet
    "--validator-addr" wallet
    "--amount" amount))

(defn harvest-validator []
  (clojure.java.shell/sh 
    "../hmy"
    "--node=https://api.s0.os.hmny.io"
    "staking"
    "collect-rewards"
    "--delegator-addr" wallet))

(defn get-validator-info []
  (let [response 
        (clojure.java.shell/sh
          "../hmy"
          "--node=https://api.s0.os.hmny.io"
          "blockchain" "validator"
          "information" wallet)]
    (when (or (= (:err response) "") (nil? (:err response)))
    (:result
      (json/read-str
        (:out response)
        :key-fn keyword
        )))))

(defn get-latest-headers []
  (:result
    (json/read-str
      (:out (clojure.java.shell/sh "../hmy" "blockchain" "latest-headers"))
      :key-fn keyword
      )))

(defn get-wallet-balances []
  (into (hash-map) (map (fn [[_ wallet]] [wallet (wallet-balance wallet)]) (list-wallets))))
