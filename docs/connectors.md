# Connectors

A connector is a service that steps can work with, described in one file in `backend/src/main/resources/connectors/`. Each connector is one step in the editor, such as Slack, with its actions to choose from, and its triggers start a task, each from a start tile of its own when the task has several. The format is documented in `Connector.kt`, and `ConnectorFormatTest` shows every word in use.

## What a file holds

* **name, label, description, icon** and **upstream**, the package and version it was copied from.
* **hosts**: the only hosts a request with the connector's credential may go to. A host may name a credential value, such as `{{credential.subdomain}}.zendesk.com`, and an input never decides a host.
* **baseUrl**: where the API starts. With it, every connector also offers a **Custom API call**.
* **credential**: its fields, and where they go: `headers`, `query`, `body`, `basic`, or `oauth2` to sign in with an account.
* **actions**, each with its `inputs`, its `request` (`method`, `url`, `headers`, `query`, `body`), and what it gives: `result`, `outputs`, or `download` for an answer that is a file.
* **triggers**, which ask the service every few minutes for what is new (`newBy` time or lastItem), or subscribe to its events with `webhook`.

## Inputs

`string`, `multiline`, `number`, `boolean`, `select`, `json` and `file`. A `file` input names a file the run keeps, such as one Read file read, and is sent as base64 or, with `Content-Type: multipart/form-data`, as a file part. `list` names one of the connector's `lists`, so that a person chooses the value from the service, such as a table of a base. A list is written once and shared by every input that names it, and its request can use the inputs of the action it is chosen in: the editor reads it again when one of them changes, and asks for them first when they are empty. A typed value or one from an earlier step still works, so agents send the ID itself. `fieldsFrom` gives an input a form whose fields come from the service, such as the columns of a table.

## Logic

`beforeRequest` and `afterResponse` are short JavaScript functions of `inputs`, `request` and `response`. They run with no way into Java, files or the network, and never see the credential. `call({method, url, query, headers, body})` sends another request through the connector, such as for the next page.

## Proving a connector

Every action and trigger has a fixture in `backend/src/test/resources/connector-fixtures/<connector>/`. `tools/connector-harness/record.mjs` runs the original with the fixture's inputs and records what it sends, without leaving the machine, and `ConnectorCheck` runs ours with the same inputs and compares:

```bash
cd tools/connector-harness && node record.mjs ../../backend/src/test/resources/connector-fixtures/resend/get_contact.json
cd backend && ./gradlew connectorCheckClasspath && java -cp "$(cat build/connector-check-classpath.txt)" com.dopplertask.dopplertask.ConnectorCheck resend
```

The recorder needs Node 22 or later. The check reads the files from the source folders, so a new or changed connector needs no build.
