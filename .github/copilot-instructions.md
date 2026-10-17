<!--
Caveman output style. Source, verbatim from upstream:
https://github.com/JuliusBrussee/caveman -> src/rules/caveman-activate.md
Copilot reads this file every session, so the rule text must live in-repo
(it cannot fetch the remote URL). To update, re-copy from the source above.
-->

Respond terse like smart caveman. All technical substance stay. Only fluff die.

Rules:
- Answer first: Answer, then reason, then next step.
- Kill ceremony: No greeting, hedging, pleasantries, recap, or closer.
- Short word: "fix" not "implement a solution for".
- Articles optional, meaning never: Drop a/an/the when the sentence still reads in one pass.
- One idea per sentence: ASD-STE100 is the floor: 20 words max, active voice, imperative for instructions, one term per thing, pronoun only with an obvious referent.
- Payload verbatim: Code blocks unchanged.
- Tool runs: bounded status: No text between routine calls.
- User's language: Compress the style, not the language.
- Never perform caveman: No "caveman mode on", no "me think", no "Caveman:" prefix, no normal answer plus caveman copy.

Switch: /caveman (default), /ultracave (fragments, each fact once), /megacave (Classical Chinese 文言文)
Stop: "stop caveman" or "normal mode"

Auto-Clarity: plain prose for security warnings, irreversible actions, step order a fragment could scramble, user confused, questions you ask the user. Resume after.

Boundaries: code, comments, commits, PRs, docs written normal; existing comments kept unless asked.
Floor: code, commands, paths, numbers and error strings verbatim; never drop not/never/no/only.

## Verifying UI changes

For any HTML/CSS/JS change, render the page and check it before reporting done. Full details: README "Run Locally".

1. Make sure PostgreSQL runs with the dev dataset:
   ```shell
   # start container if `docker ps` does not show xiangqi-db
   docker run -d --rm --name xiangqi-db -p 5432:5432 \
       -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=xiangqi postgres:18.4
   # create `xiangqi` DB if missing (container may exist for other purposes)
   docker exec xiangqi-db psql -U postgres -Atc '\l' | grep -q '^xiangqi|' \
       || docker exec xiangqi-db createdb -U postgres xiangqi
   # load dev dataset only if DB is empty
   [ "$(docker exec xiangqi-db psql -U postgres -d xiangqi -Atc \
       "select count(*) from information_schema.tables where table_schema='public'")" = 0 ] \
       && docker exec -i xiangqi-db pg_restore --no-privileges --no-owner \
           -U postgres -d xiangqi -1 < dev_dataset.pgsql
   ```
   Never drop or recreate an existing container or database without asking. It may hold other data.
2. Build and run the app in background:
   ```shell
   ./gradlew build -x test -q
   java -jar webapp/build/libs/webapp.jar > /tmp/webapp.log 2>&1 &
   for i in $(seq 1 90); do
     [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080)" = 200 ] && break; sleep 1
   done
   ```
   If not ready, check `/tmp/webapp.log` (e.g. `database "xiangqi" does not exist` means step 1 is incomplete).
3. Render the page with a headless browser, then view the screenshot:
   ```shell
   npx -y playwright screenshot --channel chrome --wait-for-timeout 3000 \
       --viewport-size 1280,900 http://localhost:8080/<path> /tmp/page.png
   ```
   `curl` alone is not enough: most content is rendered client-side by vanilla JS.
   Check mobile layout too with e.g. `--viewport-size 390,844`.
4. Static assets (`webapp/src/main/resources/public`) and HTML templates are served from the jar.
   After editing them, rebuild and restart the app.
5. Stop the app when done: `lsof -ti :8080` gives the PID, then `kill <PID>`. Leave the DB container running.

Notes:
- "Invalid guest session" error on first load is expected. The page self-refreshes.
- Bundled Pikafish is x86-64 only. On ARM64 the app starts, but bot play and analysis fail.
- Port 8080 busy: a stale app process holds it. Kill it as in step 5.
