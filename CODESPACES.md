# Using GitHub Codespaces

This repo includes a [`.devcontainer/devcontainer.json`](.devcontainer/devcontainer.json)
that sets up JDK 21 + the pinned Maven wrapper (`mvnw`) and runs the API in Quarkus dev
mode automatically.

## Start a Codespace

1. On GitHub, click **Code -> Codespaces -> Create codespace on main**.
2. Wait for the container to build (first time only) - `postCreateCommand` pre-warms
   the Maven dependency cache.
3. On start, `postStartCommand` runs `./mvnw quarkus:dev` automatically. Watch the
   terminal for `Listening on: http://0.0.0.0:8080`.

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
- **Code -> Codespaces -> ... -> Delete** to remove it entirely once you're done.

## Troubleshooting

- If `quarkus:dev` doesn't start, open a terminal and run `./mvnw quarkus:dev`
  manually to see the error.
- If the forwarded port doesn't respond, confirm the log shows
  `http://0.0.0.0:8080`, not `http://localhost:8080` - the latter means the
  `%dev.quarkus.http.host=0.0.0.0` setting in
  [application.properties](src/main/resources/application.properties) didn't take
  effect.
