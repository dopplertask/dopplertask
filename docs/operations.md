# Running DopplerTask

## Other ways to install

**Docker.** The `Dockerfile` builds the current code:
```bash
docker build -t dopplertask/dopplertask .
docker run -d -p 8090:8090 -p 61614:61614 -v $HOME:/root dopplertask/dopplertask
```
`docker compose up -d` runs the image `dopplertask/dopplertask:latest` and keeps your tasks in a named volume. The image on Docker Hub is version 0.14.7 from 2022 until the next release is published.

**Command line.** To run tasks from a terminal, install the tool from the `cli` folder with `npm install -g .`, then use `dopplertask run [taskname]` and `dopplertask --help`.

To change the editor or the command line tool, use Node 26, which `.nvmrc` names.

## Running it around the clock
* **Restart it automatically.** The compose file does this with `restart: unless-stopped`. The image exits when it runs out of memory, so that it comes back instead of limping along.
* **Watch `GET /health`,** which needs no token. It answers 503 and says why when the database does not answer or the queue for runs is full, also when the pages still load.
* **Runs that a restart interrupts are marked as failed** when the server starts, with the reason in their output. Runs waiting in the queue at that moment are also marked, since the queue is kept in memory.
* **Decide how long to keep runs.** Everything is kept unless `DOPPLERTASK_KEEP_RUNS_DAYS` is set, and the database grows with every run and the size of its output. With it, runs that ended longer ago than that are deleted.
* **Back up the database** by copying `~/dopplerdb.mv.db` while the server is stopped.
* **A step has no time limit of its own.** Up to about 40 to 50 runs go at once, so steps that hang forever can use up the room for new runs. Use commands that have their own timeout.

## Credentials
What a step signs in with is kept apart from tasks, under **Credentials** in the editor or through the API. A step only has `credential`, the name of one of the type it needs, so a task can be shared, exported and read by an agent without exposing anything. Steps that sign in to the same service share a type: Email and Read email use a `Mail account`, SSH and SCP a `Server login`, Google Sheets, Drive and Calendar a `Google service account`, and Jira and Confluence an `Atlassian account`. Other steps have a type of their own, named after the step.

Create one under Credentials, or with New on a step:
```
curl -X POST localhost:8090/credentials -H 'Content-Type: application/json' -d '{"name":"prod-slack","type":"SlackAction","values":{"token":"xoxb-..."}}'
```
* `GET /credentials`: names, types, the steps each one fits and which tasks use them. Never the values.
* `PUT /credentials/{name}`: change values. A field left out or empty keeps its value.
* `DELETE /credentials/{name}`: refused while the newest version of a task uses it.

The fields of a step that are in a credential, such as `token`, `password` or `apiKey`, are marked as such in `GET /task/actions`, with the category `SECRET` or `CREDENTIAL`. If a task is saved with such a value written in the step, as in a task from an earlier version, the value becomes a credential. An agent is refused instead. An HTTP step's `Authorization` or key header moves the same way, and the HTTP step has one such header in a credential.

## Upgrading
Existing tasks, versions and webhook addresses keep working, and the database is updated when the new version starts. A few things behave differently since version 0.14.7:

* **An HTTP step fails on a status of 400 or above.** This also applies to steps built on it, such as OpenAI. A task that should carry on needs Continue on failure on that step.
* **The database gets a new format.** H2 2 cannot open files from H2 1.4, so at the first start the file is converted, which fetches H2 1.4.199 from Maven Central once, and the old file is kept with the ending `.mv.db.bak`. It holds the old secrets, so delete it when the upgrade has been checked. There is no way back to the earlier version without that file.
* **Secrets move out of steps.** At the first start they become credentials, named like `slack-1`, and the old columns are removed, so back up the database first. Set `DOPPLERTASK_ENCRYPTION_KEY` to the same value as before if you had one. Tasks no longer return their secrets, and scripts that read them from the API need another way.
* **A webhook with a sign-in that has no value refuses everyone.** It used to accept an empty one.
* **The OpenAI step verifies the certificate of the server.** A server of your own with a self-signed certificate needs that certificate added to the trust store of Java.
* **Requests from web pages on other sites are refused.** A page that should have access needs `dopplertask.allowedOrigins`. Webhooks and clients that are not browsers are not affected.
* **The command line tool exits with 1 when a run fails,** and prints the output of each step rather than internal log lines.
* **The editor no longer uses port 61614,** so it does not need to be published for the editor.

## Security
DopplerTask runs commands on the machine it is installed on, so treat access to it as access to that machine.

* **Protect access with tokens.** Without them anyone who can reach port 8090 can create and run tasks. Set `DOPPLERTASK_PERSON_TOKEN` to require a token everywhere except webhooks, which have their own authentication. The editor asks for it, and the command line tool reads `DOPPLERTASK_TOKEN`.
* **Or sign in through your company's identity provider.** Any provider with OpenID Connect works. Register an application with the redirect address `https://your-host/sso/callback`, and set `DOPPLERTASK_SSO_ISSUER`, `DOPPLERTASK_SSO_CLIENT_ID` and `DOPPLERTASK_SSO_CLIENT_SECRET`. `DOPPLERTASK_SSO_ALLOWED_DOMAINS` limits who can sign in, such as `example.com`, and `DOPPLERTASK_SSO_REDIRECT_URI` sets the address if a proxy hides it. Without an allowed domain, everyone the provider accepts can sign in. Sessions last 12 hours and end when the server restarts.
* **Give agents their own token,** with `DOPPLERTASK_AGENT_TOKEN`. What an agent saves is a proposal: runs, webhooks and schedules keep using the version before it until a person approves it in the editor or with `POST /task/{checksum}/approve`. An agent can read tasks and results and run approved tasks, but it cannot turn a task on, delete anything or run a single action. Passwords and keys are masked for it, and sending the mask back keeps the real value. The output of a run is not masked, so a step that prints a secret shows it to the agent.
* **Web pages on other sites are refused,** unless they are listed in `dopplertask.allowedOrigins`.
* **Steps never hold secrets.** A step names a credential, and gets its values only while it runs. No API, export or agent can read a value back, and a value that a step or a service repeats is hidden from the stored output.
* **Agents can use credentials but not read, create or change them.** A step can still send its credential to the address it is given, so review the address when approving a proposal.
* **Keep the encryption key safe.** Credentials use AES-256-GCM. Without `DOPPLERTASK_ENCRYPTION_KEY`, a key is made the first time one is saved, in `~/.dopplertask/encryption.key` readable only by its owner, or at `DOPPLERTASK_KEY_FILE`. Back it up along with the database, since credentials cannot be read without it.
* **Port 61614 uses the default password of the message broker,** and any web page can reach it. It lets a page read the output of runs and start tasks. It is turned off when a person token is set. Only the older command line tool needs this port, so do not publish it unless you use that tool.
