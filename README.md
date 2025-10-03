# Cronut-Integrant: Integrant bindings for Cronut

[![Cronut Integrant Test](https://github.com/factorhouse/cronut-integrant/actions/workflows/ci.yml/badge.svg)](https://github.com/factorhouse/cronut-integrant/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-integrant.svg)](https://clojars.org/io.factorhouse/cronut-integrant)

# Summary

Cronut-Integrant provides bindings for [Cronut](https://github.com/factorhouse/cronut)
to [Integrant](https://github.com/weavejester/integrant), the DI micro-framework.

Compatible with either [Cronut](https://github.com/factorhouse/cronut)
or [Cronut-Javax](https://github.com/factorhouse/cronut-javax) depending on your requirement of Jakarta or Javax.

## Related Projects

| Project                                                     | Desription                                                                                                   | Clojars Project                                                                                                                         |
|-------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| [cronut](https://github.com/factorhouse/cronut)             | Cronut with [Jakarta](https://en.wikipedia.org/wiki/Jakarta_EE) support (Primary)                            | [![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut.svg)](https://clojars.org/io.factorhouse/cronut)             |
| [cronut-javax](https://github.com/factorhouse/cronut-javax) | Cronut with [Javax](https://jakarta.ee/blogs/javax-jakartaee-namespace-ecosystem-progress/) support (Legacy) | [![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/cronut-javax.svg)](https://clojars.org/io.factorhouse/cronut-javax) |

# Contents

- [Configuration](#configuration)
    * [`:cronut/scheduler` definition](#cronutscheduler-definition)
        + [Scheduler example](#scheduler-example)
    * [`:job` definition](#job-definition)
        + [Job example](#job-example)
    * [`:trigger` definition](#trigger-definition)
        + [`:trigger` tagged literals](#trigger-tagged-literals)
            - [`#cronut/cron`: Simple Cron Scheduling](#cronutcron-simple-cron-scheduling)
            - [`#cronut/interval`: Simple Interval Scheduling](#cronutinterval-simple-interval-scheduling)
            - [`#cronut/trigger`: Full trigger definition](#cronuttrigger-full-trigger-definition)
    * [Concurrent execution](#concurrent-execution)
        + [Global concurrent execution](#global-concurrent-execution)
        + [Job-specific concurrent execution](#job-specific-concurrent-execution)
        + [Misfire configuration](#misfire-configuration)
- [System initialization](#system-initialization)
- [Example system](#example-system)
    * [Configuration](#configuration-1)
    * [Job definitions](#job-definitions)
    * [Helper functions](#helper-functions)
    * [Putting it together](#putting-it-together)
        + [Starting the system](#starting-the-system)
        + [Logs of the running system](#logs-of-the-running-system)
        + [Stopping the system](#stopping-the-system)
- [License](#license)

# Configuration

A quartz `scheduler` runs a `job` on a schedule defined by a `trigger`.

A `job` or `trigger` is uniquely identified by a `key` consisting of a `name` and (optional) `group`.

A `job` can have multiple `triggers`, a `trigger` is for a single `job` only.

## `:cronut/scheduler` definition

Cronut provides access to the Quartz Scheduler, exposed via Integrant with `:cronut/scheduler`

The scheduler supports the following fields:

1. `:schedule`: `items` to schedule, each being a map containing a :job:, :opts, and :trigger
2. `:concurrent-execution-disallowed?`: run all jobs with @DisableConcurrentExecution
3. `:update-check?`: check for Quartz updates on system startup

### Scheduler example

````clojure
 :cronut/scheduler {:update-check?                    false
                    :concurrent-execution-disallowed? true
                    :schedule
                    [;; basic interval
                     {:job     #ig/ref :test.job/one
                      :opts    {:description "test job 1, identity auto-generated"}
                      :trigger #cronut/trigger {:type      :simple
                                                :interval  2
                                                :time-unit :seconds
                                                :repeat    :forever}}

                     ;; full interval
                     {:job     #ig/ref :job/two
                      :opts    #ig/ref :job/two-opts
                      :trigger #cronut/trigger {:type        :simple
                                                :interval    3000
                                                :repeat      :forever
                                                :name        "trigger-two"
                                                :group       "test-group"
                                                :description "test trigger"
                                                :start       #inst "2019-01-01T00:00:00.000-00:00"
                                                :end         #inst "2019-02-01T00:00:00.000-00:00"
                                                :priority    5}}
````

## `:job` definition

The `:job` in every scheduled item must implement the org.quartz.Job interface

The expectation being that every 'job' in your Integrant system will reify that interface, either directly via `reify`
or by returning a `defrecord` that implements the interface. e.g.

````clojure
(defmethod ig/init-key :test.job/one
  [_ config]
  (reify Job
    (execute [this job-context]
      (log/info "Reified Impl:" config))))

(defrecord TestDefrecordJobImpl [identity description recover? durable? test-dep]
  Job
  (execute [this job-context]
    (log/info "Defrecord Impl:" this)))

(defmethod ig/init-key :test.job/two
  [_ config]
  (map->TestDefrecordJobImpl config))
````

Cronut supports further Quartz configuration of jobs (identity, description, recovery, and durability) by configuring an
optional `opts` map for each scheduled item.

### Job integrant configuration

**Job definition**

````clojure
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
````

**Job options**

````clojure
:job/two          {}
:job/two-opts     {:name        "job2"
                   :group       "group1"
                   :description "test job 2, identity by name and group"
                   :recover?    true
                   :durable?    false
                   :dep-one     #ig/ref :dep/one
                   :dep-two     #ig/ref :test.job/one}
````                    

## `:trigger` definition

The `:trigger` in every scheduled item must resolve to an org.quartz.Trigger of some variety or another, to ease that
resolution Cronut provides the following tagged literals:

### `:trigger` tagged literals

#### `#cronut/cron`: Simple Cron Scheduling

A job is scheduled to run on a cron by using the `#cronut/cron` tagged literal followed by a valid cron expression

The job will start immediately when the system is initialized, and runs in the default system time-zone

````clojure
:trigger #cronut/cron "*/8 * * * * ?"
````

#### `#cronut/interval`: Simple Interval Scheduling

A job is scheduled to run periodically by using the `#cronut/interval` tagged literal followed by a milliseconds value

````clojure
:trigger #cronut/interval 3500
````

#### `#cronut/trigger`: Full trigger definition

Both `#cronut/cron` and `#cronut/interval` are effectively shortcuts to full trigger definition with sensible defaults.

The `#cronut/trigger` tagged literal supports the full set of Quartz configuration triggers:

````clojure
;; interval
:trigger #cronut/trigger {:type        :simple
                          :interval    3000
                          :repeat      :forever
                          :name        "trigger-two"
                          :group       "test-group"
                          :description "sample simple trigger"
                          :start       #inst "2019-01-01T00:00:00.000-00:00"
                          :end         #inst "2019-02-01T00:00:00.000-00:00"
                          :misfire     :ignore
                          :priority    5}

;;cron
:trigger #cronut/trigger {:type        :cron
                          :cron        "*/6 * * * * ?"
                          :name        "trigger-five"
                          :group       "test-group"
                          :description "sample cron trigger"
                          :start       #inst "2018-01-01T00:00:00.000-00:00"
                          :end         #inst "2029-02-01T00:00:00.000-00:00"
                          :time-zone   "Australia/Melbourne"
                          :misfire     :fire-and-proceed
                          :priority    4}
````

## Concurrent execution

### Global concurrent execution

Set `:concurrent-execution-disallowed?` on the scheduler to disable concurrent execution of all jobs.

### Job-specific concurrent execution

Set `:disallow-concurrent-execution?` on a specific job to disable concurrent execution of that job only.

### Misfire configuration

If you disable concurrent job execution ensure you understand Quartz Misfire options and remember to set
`org.quartz.jobStore.misfireThreshold=[some ms value]` in your quartz.properties file. See Quartz documentation for more
information.

See our test-resources/config.edn and test-resources/org/quartz/quartz.properties for examples of misfire threshold and
behaviour configuration.

# System initialization

When initializing an Integrant system you will need to provide the Cronut data readers.

See: `cronut/data-readers` for convenience.

````clojure
(def data-readers
  {'cronut/trigger  cronut/trigger-builder
   'cronut/cron     cronut/shortcut-cron
   'cronut/interval cronut/shortcut-interval})
````

e.g.

````clojure
(defn init-system
  "Convenience for starting integrant systems with cronut data-readers"
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cronut/data-readers readers)} config))))
````

# Example system

This repository contains an example system composed of of integratant configuration, job definitions, and helper
functions, see the [test](/test) directory.

## Configuration

Integrant configuration source: [dev-resources/config.edn](dev-resources/config.edn).

````clojure
{:dep/one          {:a 1}

 :job/one          {:dep-one #ig/ref :dep/one}

 :job/two          {}
 :job/two-opts     {:name        "job2"
                    :group       "group1"
                    :description "test job 2, identity by name and group"
                    :recover?    true
                    :durable?    false
                    :dep-one     #ig/ref :dep/one
                    :dep-two     #ig/ref :job/one}

 :job/three        {}

 :cronut/scheduler {:update-check?                    false
                    :concurrent-execution-disallowed? true
                    :schedule                         [;; basic interval
                                                       {:job     #ig/ref :job/one
                                                        :opts    {:description "test job 1, identity auto-generated"}
                                                        :trigger #cronut/trigger {:type      :simple
                                                                                  :interval  2
                                                                                  :time-unit :seconds
                                                                                  :repeat    :forever}}

                                                       ;; full interval
                                                       {:job     #ig/ref :job/two
                                                        :opts    #ig/ref :job/two-opts
                                                        :trigger #cronut/trigger {:type        :simple
                                                                                  :interval    3000
                                                                                  :repeat      :forever
                                                                                  :name        "trigger-two"
                                                                                  :group       "test-group"
                                                                                  :description "test trigger"
                                                                                  :start       #inst "2019-01-01T00:00:00.000-00:00"
                                                                                  :end         #inst "2019-02-01T00:00:00.000-00:00"
                                                                                  :priority    5}}

                                                       ;; shortcut interval
                                                       {:job     #ig/ref :job/two
                                                        :opts    #ig/ref :job/two-opts
                                                        :trigger #cronut/interval 3500}

                                                       ;; basic cron
                                                       {:job     #ig/ref :job/two
                                                        :opts    #ig/ref :job/two-opts
                                                        :trigger #cronut/trigger {:type :cron
                                                                                  :cron "*/4 * * * * ?"}}

                                                       ;; full cron
                                                       {:job     #ig/ref :job/two
                                                        :opts    #ig/ref :job/two-opts
                                                        :trigger #cronut/trigger {:type        :cron
                                                                                  :cron        "*/6 * * * * ?"
                                                                                  :name        "trigger-five"
                                                                                  :group       "test-group"
                                                                                  :description "another-test trigger"
                                                                                  :start       #inst "2018-01-01T00:00:00.000-00:00"
                                                                                  :end         #inst "2029-02-01T00:00:00.000-00:00"
                                                                                  :time-zone   "Australia/Melbourne"
                                                                                  :priority    4}}

                                                       ;; shortcut cron
                                                       {:job     #ig/ref :job/two
                                                        :opts    #ig/ref :job/two-opts
                                                        :trigger #cronut/cron "*/8 * * * * ?"}

                                                       ;; Note: This job misfires because it takes 7 seconds to run, but runs every 5 seconds, and isn't allowed to run concurrently with {:disallowConcurrentExecution? true}
                                                       ;;       So every second job fails to run, and is just ignored with the :do-nothing :misfire rule
                                                       {:job     #ig/ref :job/three
                                                        :opts    {:name        "job3"
                                                                  :description "test job 3, identity by name only - default group"}
                                                        :trigger #cronut/trigger {:type    :cron
                                                                                  :cron    "*/5 * * * * ?"
                                                                                  :misfire :do-nothing}}]}}
````

## Job definitions

Job definitions source: [test/job.clj](test/job.clj)

```clojure
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
```

## Helper functions

Helper functions source: [test/cronut/integration-test.clj](test/cronut/integration_test.clj)

````clojure
(defn init-system
  "Example of starting integrant cronut systems with data-readers"
  ([]
   (init-system (slurp (io/resource "config.edn"))))
  ([config]
   (init-system config nil))
  ([config readers]
   (ig/init (ig/read-string {:readers (merge cig/data-readers readers)} config))))

(defn halt-system
  "Example of stopping integrant cronut systems"
  [system]
  (ig/halt! system))
````

## Putting it together

### Starting the system

```clojure
(do
  (require '[cronut.integration-test :as test])
  (test/init-system))
```

### Logs of the running system

```bash
Connecting to local nREPL server...
Clojure 1.12.3
nREPL server started on port 54313 on host 127.0.0.1 - nrepl://127.0.0.1:54313
(do
  (require '[cronut.integration-test :as test])
  (test/init-system))
17:49:32.071 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – initializing scheduler
17:49:32.072 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – with quartz update check disabled
17:49:32.081 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.impl.StdSchedulerFactory – Using default implementation for ThreadExecutor
17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] o.quartz.core.SchedulerSignalerImpl – Initialized Scheduler Signaller of type: class org.quartz.core.SchedulerSignalerImpl
17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – Quartz Scheduler v2.4.0 created.
17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.simpl.RAMJobStore – RAMJobStore initialized.
17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – Scheduler meta-data: Quartz Scheduler (v2.4.0) 'CronutScheduler' with instanceId 'NON_CLUSTERED'
  Scheduler class: 'org.quartz.core.QuartzScheduler' - running locally.
  NOT STARTED.
  Currently in standby mode.
  Number of jobs executed: 0
  Using thread pool 'org.quartz.simpl.SimpleThreadPool' - with 6 threads.
  Using job-store 'org.quartz.simpl.RAMJobStore' - which does not support persistence. and is not clustered.

17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.impl.StdSchedulerFactory – Quartz scheduler 'CronutScheduler' initialized from default resource file in Quartz package: 'quartz.properties'
17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.impl.StdSchedulerFactory – Quartz scheduler version: 2.4.0
17:49:32.085 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – with global concurrent execution disallowed
17:49:32.086 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – JobFactory set to: cronut.job$factory$reify__2885@6b1b29a8
17:49:32.086 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling [7] jobs
17:49:32.088 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new job DEFAULT.6da64b5bd2ee-bad9b344-eb35-48c2-a2e9-74a16dae0b7a {:description test job 1, identity auto-generated}
17:49:32.089 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new job group1.job2 {:name job2, :group group1, :description test job 2, identity by name and group, :recover? true, :durable? false, :dep-one {:a 1}, :dep-two #object[job$eval13079$fn$reify__13081 0x12d12097 job$eval13079$fn$reify__13081@12d12097]}
17:49:32.089 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new trigger for existing job group1.job2 {:name job2, :group group1, :description test job 2, identity by name and group, :recover? true, :durable? false, :dep-one {:a 1}, :dep-two #object[job$eval13079$fn$reify__13081 0x12d12097 job$eval13079$fn$reify__13081@12d12097]}
17:49:32.090 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new trigger for existing job group1.job2 {:name job2, :group group1, :description test job 2, identity by name and group, :recover? true, :durable? false, :dep-one {:a 1}, :dep-two #object[job$eval13079$fn$reify__13081 0x12d12097 job$eval13079$fn$reify__13081@12d12097]}
17:49:32.090 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new trigger for existing job group1.job2 {:name job2, :group group1, :description test job 2, identity by name and group, :recover? true, :durable? false, :dep-one {:a 1}, :dep-two #object[job$eval13079$fn$reify__13081 0x12d12097 job$eval13079$fn$reify__13081@12d12097]}
17:49:32.091 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new trigger for existing job group1.job2 {:name job2, :group group1, :description test job 2, identity by name and group, :recover? true, :durable? false, :dep-one {:a 1}, :dep-two #object[job$eval13079$fn$reify__13081 0x12d12097 job$eval13079$fn$reify__13081@12d12097]}
17:49:32.091 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] cronut – scheduling new job DEFAULT.job3 {:name job3, :description test job 3, identity by name only - default group}
17:49:32.091 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – Scheduler CronutScheduler_$_NON_CLUSTERED started.
=>
{:dep/one {:a 1},
 :job/one #object[job$eval13079$fn$reify__13081 0x12d12097 "job$eval13079$fn$reify__13081@12d12097"],
 :job/three #object[job$eval13106$fn$reify__13108 0x3ce9555 "job$eval13106$fn$reify__13108@3ce9555"],
 :job/two #job.TestDefrecordJobImpl{},
 :job/two-opts {:name "job2",
                :group "group1",
                :description "test job 2, identity by name and group",
                :recover? true,
                :durable? false,
                :dep-one {:a 1},
                :dep-two #object[job$eval13079$fn$reify__13081 0x12d12097 "job$eval13079$fn$reify__13081@12d12097"]},
 :cronut/scheduler #object[org.quartz.impl.StdScheduler 0x13778940 "org.quartz.impl.StdScheduler@13778940"]}
17:49:32.094 INFO  [CronutScheduler_Worker-1] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:32.094 INFO  [CronutScheduler_Worker-2] job – Reified Impl: {:dep-one {:a 1}}
17:49:32.096 INFO  [CronutScheduler_Worker-3] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:32.096 INFO  [CronutScheduler_Worker-4] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:32.097 INFO  [CronutScheduler_Worker-5] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:34.059 INFO  [CronutScheduler_Worker-6] job – Reified Impl: {:dep-one {:a 1}}
17:49:35.006 INFO  [CronutScheduler_Worker-1] job – b41561ba-7aa9-407f-ae68-20c268dfbd33 Reified Impl (Job Delay 7s): {}
17:49:35.569 INFO  [CronutScheduler_Worker-2] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:36.006 INFO  [CronutScheduler_Worker-3] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:36.007 INFO  [CronutScheduler_Worker-4] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:36.062 INFO  [CronutScheduler_Worker-5] job – Reified Impl: {:dep-one {:a 1}}
17:49:38.063 INFO  [CronutScheduler_Worker-6] job – Reified Impl: {:dep-one {:a 1}}
17:49:39.066 INFO  [CronutScheduler_Worker-2] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:40.006 INFO  [CronutScheduler_Worker-3] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:40.008 INFO  [CronutScheduler_Worker-4] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:40.062 INFO  [CronutScheduler_Worker-5] job – Reified Impl: {:dep-one {:a 1}}
17:49:42.005 INFO  [CronutScheduler_Worker-6] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:42.013 INFO  [CronutScheduler_Worker-1] job – b41561ba-7aa9-407f-ae68-20c268dfbd33 Finished
17:49:42.062 INFO  [CronutScheduler_Worker-2] job – Reified Impl: {:dep-one {:a 1}}
17:49:42.569 INFO  [CronutScheduler_Worker-3] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:44.006 INFO  [CronutScheduler_Worker-4] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
17:49:44.063 INFO  [CronutScheduler_Worker-5] job – Reified Impl: {:dep-one {:a 1}}
17:49:45.001 INFO  [CronutScheduler_Worker-6] job – a6805418-0a31-44f2-8c55-e686152f800f Reified Impl (Job Delay 7s): {}
17:49:46.062 INFO  [CronutScheduler_Worker-1] job – Reified Impl: {:dep-one {:a 1}}
17:49:46.062 INFO  [CronutScheduler_Worker-2] job – Defrecord Impl: #job.TestDefrecordJobImpl{}
(test/halt-system *1)
17:49:46.984 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – Scheduler CronutScheduler_$_NON_CLUSTERED shutting down.
17:49:46.984 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – Scheduler CronutScheduler_$_NON_CLUSTERED paused.
17:49:46.984 INFO  [nREPL-session-6fa69f5a-b19c-48cf-9b7f-20bda624be80] org.quartz.core.QuartzScheduler – Scheduler CronutScheduler_$_NON_CLUSTERED shutdown complete.
=> nil
17:49:52.007 INFO  [CronutScheduler_Worker-6] job – a6805418-0a31-44f2-8c55-e686152f800f Finished
```

### Stopping the system

```clojure
(test/halt-system *1)
```

# License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
