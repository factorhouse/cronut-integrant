(ns job
  (:require [clojure.tools.logging :as log])
  (:import (org.quartz Job)))

(defrecord TestDefrecordJobImpl []
  Job
  (execute [this _job-context]
    (log/info "Defrecord Impl:" this)))

;; These two functions are called by Integrant on system startup, see:
;; https://github.com/weavejester/integrant?tab=readme-ov-file#initializer-functions
(defn two
  [config]
  (map->TestDefrecordJobImpl config))

(def two-opts identity)