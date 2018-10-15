(ns katamari.server.nrepl-server
  (:require [nrepl.server :refer [start-server stop-server]]))

(defonce +instance+
  (atom nil))

(defn nrepl-handler []
  (require 'cider.nrepl)
  (ns-resolve 'cider.nrepl 'cider-nrepl-handler))

(defn stop-nrepl-server! []
  (swap! +instance+
         (fn [i]
           (when i
             (stop-server i)
             nil))))

(defn start-nrepl-server! [cfg]
  (swap! +instance+
         (fn [i]
           (when i
             (stop-server i))
           (start-server
            :port (Long/parseLong (get cfg :server-nrepl-port))
            :host (get cfg :server-addr "0.0.0.0")
            :handler (nrepl-handler)))))
