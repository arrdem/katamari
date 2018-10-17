(ns katamari.server.web-server
  "Katamari's API server.

  A web server which makes it possible to boot Katamari once and amortize its startup cost across
  potentially many build or code evaluation requests."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.java.io :as jio]
            [clojure.java.classpath :as jcp]
            [clojure.string :as str]
            [clojure.tools.logging :as log]

            [compojure.core :refer [context defroutes GET PUT POST]]
            [cheshire.core :as json]

            [ring.util.response :as resp]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-response]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.session :as session]

            [katamari.conf :as conf]
            ;; The embedded nREPL server
            [katamari.server.nrepl-server :refer [start-nrepl-server!]]
            ;; Tasks
            [katamari.server.extensions :refer [get-middleware-stack]]))

;;;; Config crap

(defn key-fn [k]
  (keyword (.replaceAll k "_" "-")))

(defn json-response
  "Helper for returning JSON coded responses.

  JSON codes the given object in a single shot, returning a 200 response by default unless the user
  specifies an optional status code."
  ([obj]
   (json-response obj 200))
  ([obj code]
   (-> obj
       (json/encode)
       (resp/response)
       (resp/status code)
       (resp/update-header "content-type" (constantly "application/json")))))

;;;; The server routes

(defroutes +api-v0+
  (context "/api/v0" []
    (GET "/ping" []
      (-> {"status" "ok"
           "ts"     (System/currentTimeMillis)}
          (json-response 200)))

    (GET "/request" {:keys [body]}
      (let [{:keys [request config-file repo-root]
             :as   json-body} (json/parse-string (slurp body) key-fn)
            config          (-> (conf/load config-file key-fn)
                                (merge (dissoc json-body :request)))
            middleware (get-middleware-stack)]
        ;; Note that this enables the middleware stack to recurse
        (middleware config middleware request)))))

(defroutes +app+
  +api-v0+)

;; FIXME (arrdem 2018-09-29):
;;   Use component maybe?
(defonce +instance+
  (atom nil))

(defn start-web-server! [cfg]
  (let [jetty-cfg {:port (Long/parseLong (:server-http-port cfg "3636"))
                   :host (:server-addr cfg "127.0.0.1")
                   :join? false}
        jetty-inst (-> #'+app+
                       session/wrap-session
                       wrap-json-response
                       wrap-stacktrace
                       (run-jetty jetty-cfg))]
    (reset! +instance+ jetty-inst)))

(defn stop-web-server! []
  (swap! +instance+
         #(when-let [inst %]
            (.stop %)
            nil)))

(defn -main
  [config-file]
  (log/info "Loading config file" config-file)
  (let [cfg (conf/load config-file key-fn)]
    (log/info "Loaded config" cfg)
    (doseq [path (:server-extensions cfg)]
      (try (load path)
           (log/infof "Loaded extension %s" path)
           (catch Exception e
             (log/error e "Failed to load extension!"))))
    (start-web-server! cfg)
    (start-nrepl-server! cfg)))
