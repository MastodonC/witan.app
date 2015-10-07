# witan.app

Back-end for the Witan application (see <http://data.london.gov.uk/blog/an-update-on-witan-the-flexible-city-modelling-platform/> for the official announcement).

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

(with Clojure 1.7.0+)

Data Store: Cassandra

Example Configuration in <https://github.com/MastodonC/witan.app/blob/master/resources/dev.witan-app.edn>: this file is also the default. If necessary, copy to home under .witan-app.edn and modify to suit.

## Running

To start a web server for the application, run:

    lein ring server

The API documentation (via swagger) will automaticall open on http://localhost:3000

## License

Copyright © 2015 Mastodon C

