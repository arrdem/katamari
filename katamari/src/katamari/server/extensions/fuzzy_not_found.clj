(ns katamari.server.extensions.fuzzy-not-found
  "Fuzzy correction instead of just not-found."
  {:authors ["Reid 'arrdem' McKenzie <me@arrdem.com>"]}
  (:require [clojure.string :as str]
            [katamari.server.extensions :refer [defwrapper]]
            [ring.util.response :as resp]
            [clj-fuzzy.metrics :as fuz]))

(defwrapper wrap-not-found
  [handler config stack request]
  (let [resp (handler config stack request)]
    (if (and (= 400 (:status resp))
             (= "No handler for request" (:msg (:body resp))))
      (let [meta (-> (stack config stack ["meta"])
                     :body :metadata)
            word (.replaceAll (first request) "-" "")
            candidates (->> meta
                            (map :kat/task-name)
                            (sort-by #(fuz/jaro word (.replaceAll % "-" ""))
                                     #(> %1 %2))
                            (take 3))]
        (-> {:intent :msg
             :msg (->> candidates
                       (map #(str "  " %))
                       (str/join "\n")
                       (str "No handler found for request:\n"
                            "  " (pr-str request) "\n"
                            "\n"
                            "Did you mean one of:\n"))
             :request request
             :candidates candidates}
            (resp/response)
            (resp/status 400)))
      resp)))
