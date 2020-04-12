(ns dashboard.core
  (:gen-class)
  (:require 
    [dashboard.handler :refer [app event-msg-handler* scheduler]]
   [environ.core :refer [env]]
   [system.core :refer [defsystem]]
   [system.repl :refer [set-init! go]]
   (system.components
    [http-kit :refer [new-web-server]] 
    [sente :refer [new-channel-socket-server]]
    [repl-server :refer [new-repl-server]]
    [hara-io-scheduler :refer [new-scheduler]]
   )
   [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
))

(defsystem dev-system
  [:web (new-web-server (Integer. (env :http-port)) app)
   :sente (new-channel-socket-server event-msg-handler* sente-web-server-adapter 
          {:user-id-fn (fn [{:keys [session] :as req}]
                         (println "NEW REQ" (str session))
                         (:uid session)
                         )
           :handshake-data-fn (fn [req]
                                (println (str "HANDSHAKE " (:session req)))
                                (let [uid (get-in req [:session :uid])]
                                  ))
           })
   :scheduler (new-scheduler scheduler)])

(defsystem prod-system
  [:web (new-web-server (Integer. (env :http-port)) app)
   :repl-server (new-repl-server (Integer. (env :repl-port)))
   ])

(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'prod-system)]
    (set-init! system)
    (go)))
