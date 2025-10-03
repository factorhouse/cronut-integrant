(ns job
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [integrant.core :as ig])
  (:import (java.util UUID)
           (org.quartz Job)))

(defmethod ig/init-key :dep/one
  [_ config]
  config)

(defmethod ig/init-key ::one
  [_ config]
  (reify Job
    (execute [_this _job-context]
      (log/info "Reified Impl:" config))))

(defrecord TestDefrecordJobImpl []
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

;; These next two functions are called by Integrant on system startup, see:
;; https://github.com/weavejester/integrant?tab=readme-ov-file#initializer-functions
(defn two
  [config]
  (map->TestDefrecordJobImpl config))

(def two-opts identity)

(defmethod ig/init-key ::three
  [_ config]
  (reify Job
    (execute [_this _job-context]
      (let [rand-id (str (UUID/randomUUID))]
        (log/info rand-id "Reified Impl (Job Delay 7s):" config)
        (async/<!! (async/timeout 7000))
        (log/info rand-id "Finished")))))