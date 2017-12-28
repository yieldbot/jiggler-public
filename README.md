# jiggler

Named after the happy, active [the Adventure Time
creature](http://adventuretime.wikia.com/wiki/Jigglers), the Jiggler is a
simple custom shortlinks service intended to basically provide global bookmarks
for a whole company.

Jiggler's docs are self-hosted. Jiggler is easiest to access via a chrome
extension that lets you just type in `j shortlink` in your browser bar. (It can
do the same thing in Firefox or other browsers by acting as a custom search
provider.)

# Moving Parts

Jiggler is a tiny service that runs as an uberjar. Internally, we run it with
an 80MiB Stack. It uses a tiny Postgres database (allocate maybe 10MiB for
it?).

# Chrome extension

The Chrome Extension's (super simple) code is in the `chrome-extension`
subdirectory here.

## Tests

Jiggler is pretty thoroughly tested. Run tests via `lein test`.

## Running Locally

To run with a simple SQLite db:

```
lein run -- --port 8666 --sqlite /tmp/jiggler.db
```

To run with default local PostgreSQL connection:

```
lein run -- --port 8666
```

### Database setup for development

#### Running PostgreSQL via docker

1. Download the postgres container with `docker pull postgres`.
2. Run the server (in the foreground) with
   `docker run --name postgres-jiggler -p 5666:5432 -e POSTGRES_PASSWORD=jiggler -e POSTGRES_USER=jiggler -e POSTGRES_DB=jiggler postgres`
3. Set up the database with
   `docker exec -i postgres-jiggler psql < resources/sql/001-init.up.sql`
4. Run jiggler with
   `PGPASSWORD=jiggler PGUSER=jiggler lein run -- --port 8010 --database-port 5666`

#### With local Postgres

1. Start PostgreSQL.
2. Create a user (e.g. `jiggler`) with some password.
3. Run `psql [db options] < resources/sql/*.up.sql`.
4. Run `psql [db options] "GRANT ALL ON ALL TABLES IN SCHEMA public TO [jiggler];"`
5. Run jiggler with
   `PGPASSWORD=[your password] PGUSER=[your user] lein run -- --port 8010`

## License

Copyright © 2017 Yieldbot

Distributed under the Apache License, Version 2.0. You may not use this library
except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

See the NOTICE file distributed with this work for additional information
regarding copyright ownership. Unless required by applicable law or agreed to
in writing, software distributed under the License is distributed on an "AS IS"
BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and limitations
under the License.
