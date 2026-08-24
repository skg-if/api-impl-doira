# Using GitHub Codespaces

This repo includes a [`.devcontainer/devcontainer.json`](.devcontainer/devcontainer.json)
that sets up JDK + the pinned Maven wrapper (`mvnw`) and runs the API in Quarkus dev
mode automatically.

## Start a Codespace

1. Go to [github.com/codespaces](https://github.com/codespaces) and click **New
   codespace** - pick this repo (`skg-if/api-impl-doira`) and the `main` branch.
   (The repo's own **Code -> Codespaces** dropdown only offers to create one and
   doesn't reliably list existing ones - the codespaces.new page is where you'll
   find every codespace you already have, running or stopped.)
2. Wait for the container to build (first time only) - `postCreateCommand` pre-warms
   the Maven dependency cache.
3. On start, `postStartCommand` runs `quarkus:dev` automatically (continuous testing
   and the interactive dev console are disabled so it doesn't sit waiting on
   keypresses). The terminal should show:

   ```
   ✔ Finishing up...
   ✔ Running postCreateCommand...
   ⠏ Running postStartCommand...
     › ./mvnw quarkus:dev -Dquarkus.test.continuous-testing=disabled -Dquarkus.console.enabled=false
   ```

   Give it a minute, then check for `Listening on: http://0.0.0.0:8080` (open the
   terminal's own dropdown to find the instance actually running this command, or
   just run the same line yourself in a fresh terminal).

## Use it

- Codespaces auto-forwards port `8080` (see the **Ports** tab). Click the forwarded
  URL, or open it directly:
  ```
  <forwarded-url>/skg-if/api/datacite/products?page_size=1
  ```
- Live reload is on - edit code and refresh the browser/re-curl to see changes, no
  restart needed.
- The port is forwarded as **private** by default (visible only to you signed into
  GitHub) - change visibility in the **Ports** tab if you need to share it.

## Stop / clean up

- Codespaces auto-stop after a period of inactivity (configurable in your GitHub
  Codespaces settings). Stopping doesn't delete it - your `.m2` cache and any
  uncommitted changes persist until you delete the codespace.
- On [github.com/codespaces](https://github.com/codespaces), use the **...** menu on
  the entry -> **Delete** to remove it entirely once you're done.

## Troubleshooting

- If `quarkus:dev` doesn't start, open a terminal and run `./mvnw quarkus:dev`
  manually to see the error.
- If the forwarded port doesn't respond, confirm the log shows
  `http://0.0.0.0:8080`, not `http://localhost:8080` - the latter means the
  `%dev.quarkus.http.host=0.0.0.0` setting in
  [application.properties](src/main/resources/application.properties) didn't take
  effect.
