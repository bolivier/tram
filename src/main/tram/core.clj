(ns ^:public tram.core
  "Main namespace for Tram."
  (:require [potemkin :refer [import-vars]]
            [tram.db]
            [tram.routes :as sut]
            [tram.tram-config]))

(import-vars [tram.routes tram-router defroutes]
             [tram.tram-config
              get-env
              get-tram-config
              get-migration-config
              get-database-config])
