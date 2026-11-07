<div align="center">

![DopplerTask - Open-source Workflow Automation](https://github.com/dopplertask/dopplertask/blob/2b88c494bc774023ecd41a92b685c980fb29158f/docs/images/dopplertask_logo.png)


<h1>DopplerTask - Task Automation</h1>

DopplerTask is a revolutionary open-source software that allows you to easily automate tasks. Whether it’s a bunch of bash scripts or just starting your car remotely, you can automate it. Build, run, reuse and share automations with anyone around the globe.

On top of all of this life-simplifying project, we are striving to make an climate friendly software that is fast, easy and consumes as little resources as possible.

![DopplerTask - GUI](docs/images/dopplertask_gui.png)
</div>

## Demo
Click [here](https://www.youtube.com/watch?v=WfuVR53GtWQ) to see a short demo of DopplerTask showing some of the capabilities of the software.

## Docker
The official image `dopplertask/dopplertask` is published on every release.

To run it with Docker Compose, keeping your tasks in a named volume:
```docker compose up -d```

To run the built docker image:
```docker run -d -p 8090:8090 -p 61614:61614 -v $HOME:/root dopplertask/dopplertask ```

To rebuild the docker image:
```docker build -t dopplertask/dopplertask .```

Access the GUI on:
```http://localhost:8090/public/index.html```


## CLI (Optional)
Install the CLI via NPM:
```npm install -g dopplertask-cli```

## Prerequisites

Install JDK 25. The Gradle wrapper in `backend` takes care of the rest. To change the editor or the command line tool, use Node 26, which `.nvmrc` names.

(Optional) Install CLI:
```npm install -g dopplertask-cli```

(Optional) Or to install the CLI from the cli directory, run:
```npm install -g .```

## Run
To run the backend, go to the backend folder and run:
```cd backend && ./gradlew clean build bootRun```

If CLI is installed as mentioned in the prerequisites, it can be run like so (See CLI help by typing dopplertask --help):
```dopplertask run [taskname]```


## Usage

To build a task through the API, start at [Build a task through the API](#build-a-task-through-the-api).

### Example
#### Add a task

Below is an example json of a Task:
```
{
  "name": "task_ya0mh2aw9j9n8tyux4fjhj",
  "description": "",
  "parameters": [],
  "actions": [
    {
      "@type": "StartAction",
      "ports": [
        {
          "externalId": "89741e7c-c0f2-2feb-3be5-42cb60c5ff4e",
          "portType": "OUTPUT"
        }
      ],
      "guiXPos": 50,
      "guiYPos": 340
    },
    {
      "@type": "PrintAction",
      "continueOnFailure": "",
      "scriptLanguage": "VELOCITY",
      "retries": "0",
      "failOn": "",
      "message": "Hello world",
      "ports": [
        {
          "externalId": "a4cc77f7-7a1c-d718-09c0-3cd3b01765b3",
          "portType": "INPUT"
        },
        {
          "externalId": "ae299ee3-2f2c-1d98-d375-ad0551090a88",
          "portType": "OUTPUT"
        }
      ],
      "guiXPos": 550,
      "guiYPos": 340
    }
  ],
  "connections": [
    {
      "source": {
        "externalId": "89741e7c-c0f2-2feb-3be5-42cb60c5ff4e"
      },
      "target": {
        "externalId": "a4cc77f7-7a1c-d718-09c0-3cd3b01765b3"
      }
    }
  ]
}
```

This file is used to add tasks to the system. You can do that by sending a request to the REST API:

```curl -X POST http://localhost:8090/task -H "Content-Type: application/json" -d @add_task.json```

#### Run a task

To run a task, send the JSON with the task name and parameters to the REST API:

```
{
  "taskName": "doppler-example",
  "parameters": {
  }
}
```

Example of the call:

```curl -X POST http://localhost:8090/schedule/task -H "Content-Type: application/json" -d @run_task.json```

`/schedule/task` queues the run and returns its id, and `GET /execution/{id}` returns its status, logs and the result of each step, including the steps finished so far while it is running. To continue a failed run at the step that failed, without repeating the steps before it, add `"resumeFromExecutionId"` with its id. Its parameters are kept unless the request gives new values. `GET /executions?taskName=name&limit=100` lists the latest runs of one task, newest first. `/schedule/directtask` takes the same JSON and waits for the result, which holds `output`, `status`, `executionId`, `logs` with every step and, if the run failed, `error`. A webhook only answers with `output`, `status` and `executionId`, since it can be called by anyone, so use `GET /execution/{id}` to see why a webhook run failed.

A web page on another site cannot change or run anything here, since requests with an `Origin` header naming another site are refused. Clients without that header, such as the command line tool, and webhooks are not affected. If the editor is used through a proxy that changes the host, set `dopplertask.allowedOrigins` to a comma separated list such as `https://tasks.example.com`.

#### Build a task through the API

The `example.com` addresses below are placeholders. Replace them with your own, since a request to them answers 404, which fails the step.

* `GET /task/actions` lists every action and its fields. A field can be nested under the options of another field, like `body` under `method` in HttpAction.
* `POST /task` saves a new version of the task, and the newest version is the one that runs. If it fails, the response says why.
* `GET /task/{name}/taskname` reads back the newest version of a task, and `GET /task/grouped` lists all tasks.
* `DELETE /task/{checksum}` deletes one version.
* Every task needs a StartAction, also when a webhook starts it.
* Actions are connected through their ports. A connection goes from the `externalId` of an OUTPUT port to the `externalId` of an INPUT port. Several OUTPUT ports can connect to the same INPUT port, so two branches can continue with the same action.
* A field of the type MAP is a list of objects, which use the option names of the field as keys.
* All fields are evaluated with Velocity by default. With `"scriptLanguage": "JAVASCRIPT"` every field of the action is evaluated as a script, so plain text must be quoted, like `"\"https://example.com\""`. This also applies to header names and values. In a script the variables are read as `parameters.get("name")` and `lastLog.getOutput()`, and the last expression is the value.

This task sends a request to another service when an order is received on a webhook:

```json
{
  "name": "notify_on_order",
  "active": true,
  "actions": [
    {"@type": "StartAction"},
    {
      "@type": "Webhook",
      "method": "POST",
      "triggerSuffix": "orders",
      "ports": [{"externalId": "webhook_out", "portType": "OUTPUT"}]
    },
    {
      "@type": "HttpAction",
      "scriptLanguage": "JAVASCRIPT",
      "url": "\"https://example.com/notify\"",
      "method": "POST",
      "body": "var order = JSON.parse(parameters.get(\"TRIGGER_body\")); \"New order from \" + order.customer",
      "ports": [{"externalId": "notify_in", "portType": "INPUT"}]
    }
  ],
  "connections": [
    {"source": {"externalId": "webhook_out"}, "target": {"externalId": "notify_in"}}
  ]
}
```

It is called with `POST /webhook/notify_on_order/Webhook/orders`.

This task uses data from two earlier responses. The first response is saved with SetVariableAction, since the next action replaces `lastLog`. JavaScript reads JSON with `JSON.parse(lastLog.getOutput())`:

```json
{
  "name": "user_summary",
  "actions": [
    {"@type": "StartAction", "ports": [{"externalId": "summary_start", "portType": "OUTPUT"}]},
    {
      "@type": "HttpAction",
      "url": "https://example.com/user",
      "method": "GET",
      "ports": [{"externalId": "user_in", "portType": "INPUT"}, {"externalId": "user_out", "portType": "OUTPUT"}]
    },
    {
      "@type": "SetVariableAction",
      "scriptLanguage": "JAVASCRIPT",
      "setVariableList": [{"name": "userName", "value": "JSON.parse(lastLog.getOutput()).name"}],
      "ports": [{"externalId": "save_in", "portType": "INPUT"}, {"externalId": "save_out", "portType": "OUTPUT"}]
    },
    {
      "@type": "HttpAction",
      "url": "https://example.com/orders",
      "method": "GET",
      "ports": [{"externalId": "orders_in", "portType": "INPUT"}, {"externalId": "orders_out", "portType": "OUTPUT"}]
    },
    {
      "@type": "HttpAction",
      "scriptLanguage": "JAVASCRIPT",
      "url": "\"https://example.com/summary\"",
      "method": "POST",
      "body": "parameters.get(\"userName\") + \" owes \" + JSON.parse(lastLog.getOutput()).total",
      "ports": [{"externalId": "summary_in", "portType": "INPUT"}]
    }
  ],
  "connections": [
    {"source": {"externalId": "summary_start"}, "target": {"externalId": "user_in"}},
    {"source": {"externalId": "user_out"}, "target": {"externalId": "save_in"}},
    {"source": {"externalId": "save_out"}, "target": {"externalId": "orders_in"}},
    {"source": {"externalId": "orders_out"}, "target": {"externalId": "summary_in"}}
  ]
}
```

This task retries a request until the other service stops answering with a server error, and then only continues if the response says that it is ok:

```json
{
  "name": "check_service",
  "actions": [
    {"@type": "StartAction", "ports": [{"externalId": "start_out", "portType": "OUTPUT"}]},
    {
      "@type": "HttpAction",
      "url": "https://example.com/status",
      "method": "GET",
      "retries": 2,
      "retryWait": "500",
      "ports": [
        {"externalId": "check_in", "portType": "INPUT"},
        {"externalId": "check_out", "portType": "OUTPUT"}
      ]
    },
    {
      "@type": "IfAction",
      "condition": "$lastLog.output.contains('ok')",
      "ports": [
        {"externalId": "if_in", "portType": "INPUT"},
        {"externalId": "if_true", "portType": "OUTPUT"},
        {"externalId": "if_false", "portType": "OUTPUT"}
      ]
    },
    {"@type": "PrintAction", "message": "The service is ok", "ports": [{"externalId": "print_in", "portType": "INPUT"}]}
  ],
  "connections": [
    {"source": {"externalId": "start_out"}, "target": {"externalId": "check_in"}},
    {"source": {"externalId": "check_out"}, "target": {"externalId": "if_in"}},
    {"source": {"externalId": "if_true"}, "target": {"externalId": "print_in"}}
  ]
}
```

### Task parameters
Task paramters are used to indicate what initial variables are needed to run a task.
They can be either required or not required, and can also have a default value.

All task parameters are put into the $parameters variable (see Important Variables).

To add task parameters manually using JSON:
```
"parameters": [
    {"name": "testVar", "description": "This is just a test var", "required": true, "defaultValue": "testasdasdasas"}
  ],
```

### Actions

#### Common variables for all actions
* scriptLanguage: VELOCITY (default), JAVASCRIPT
* ports: Defined input and output ports for the current action. A connection object connects two ports together. The next action can be reached via a connection object.

#### PrintAction
##### Variables
* message: A message to be printed

#### SSHAction
Connects to a machine via SSH and executes a command
##### Variables
* hostname: hostname to connect to
* username
* password
* command: A command to execute once connected

#### SecureCopyAction
##### Variables
* hostname: hostname to connect to
* username
* password
* sourceFilename: location of the file to be transferred
* destinationFilename: location of where the file will be placed in the remote host

#### HttpAction
##### Variables
* url
* method: GET, POST, PUT, DELETE
* body: Used with POST, PUT and PATCH.
* headers: List of headers, like `[{"headerName": "Accept", "headerValue": "text/plain"}]`

A status of 400 or above fails the step, with the response body kept as its output, and retries apply as for any other failure. To carry on anyway, set `continueOnFailure` to true. The next step can then read the status from `$lastLog.outputVariables.statusCode`, for example to treat a 404 differently. Any other rule, such as failing on a 3xx, can use failOn with `$result.outputVariables.statusCode`.

#### TelegramAction
Sends a message to a Telegram chat. The next step can read the id of the message from `$lastLog.outputVariables.messageId`.

##### Variables
* token: The token that BotFather gives for your bot.
* chat: A chat id, or a channel such as `@alerts`.
* message: Up to 4096 characters.
* baseUrl: Optional. Only change this to go through a proxy or your own Bot API server.

#### DiscordAction
Sends a message to a Discord channel through a webhook. The next step can read the id of the message from `$lastLog.outputVariables.messageId`.

##### Variables
* webhookUrl: In a credential. Made in the settings of the channel under Integrations.
* message: Up to 2000 characters.

#### PagerDutyAction
Opens, acknowledges or resolves an incident through the Events API v2. The next step can read the key of the incident from `$lastLog.outputVariables.incidentKey`.

##### Variables
* integrationKey: Shown on an Events API v2 integration of a service.
* operation: `open`, `acknowledge` or `resolve`.
* summary: What is wrong. Required to open an incident.
* severity: `critical`, `error`, `warning` or `info`.
* newIncidentKey: Optional when opening. Opening with the same key again does not open a second incident.
* incidentKey: Required to acknowledge or resolve.
* baseUrl: Optional. An account in the EU uses `https://events.eu.pagerduty.com`.

#### GitHubAction
Creates, comments on or closes an issue. The next step can read `$lastLog.outputVariables.url`, and also `issueNumber` after creating or closing.

##### Variables
* token: A personal access token, with permission to write issues in the repository.
* repository: Written as `owner/name`.
* operation: `createIssue`, `addComment` or `closeIssue`.
* issueNumber: Required to comment or close.
* title: Required to create.
* issueDescription: Used to create.
* comment: Required to comment.
* baseUrl: Optional. GitHub Enterprise uses `https://your-host/api/v3`.

#### JiraAction
Creates or comments on an issue, both in the cloud and on your own server. The next step can read `$lastLog.outputVariables.issueKey` and `url`.

##### Variables
* siteUrl: Such as `https://acme.atlassian.net`.
* email: The email of the account, for Jira in the cloud. Leave it empty on your own server.
* token: An API token in the cloud, or a personal access token on your own server.
* operation: `createIssue` or `addComment`.
* project, issueType, summary: Used to create. The type is `Task` if nothing else is said.
* issueKey: Such as `OPS-12`. Used to comment.
* issueDescription: Used to create.
* comment: Required to comment.

#### RedisAction
Reads and writes a value in Redis. A key that does not exist is not a failure, and a later step can branch on `$lastLog.outputVariables.found`.

##### Variables
* hostname, port, password: Where the server is. The port is 6379 if nothing else is said.
* useTls: `true` for most hosted Redis services.
* operation: `get`, `set`, `increment` or `delete`.
* key: The key.
* value: The value to write.
* expirySeconds: Optional. The key is removed after this time.

#### PostgresAction
Runs a query on a PostgreSQL database. Rows are given as JSON, such as `[{"id":1,"name":"Ada"}]`, and the number of rows as `$lastLog.outputVariables.rowCount`.

##### Variables
* hostname, port, database, username, password: Where the database is. The port is 5432 if nothing else is said.
* useTls: Requires an encrypted connection. The certificate of the server is not verified.
* command: The query. Write `?` where a value goes.
* queryValues: One value per line, for each `?`. Use this for text from earlier steps, since it cannot change the query.

#### EmailAction
Sends an email through a mail server. With STARTTLS or TLS nothing is sent unless the connection is encrypted and the certificate of the server is valid.

##### Variables
* hostname, port: The mail server. The port is 587 if nothing else is said.
* security: `starttls`, `tls` or `none`.
* username, password: Leave them empty if the server does not ask for them.
* from: The address to send from.
* to: One address, or several separated by commas.
* subject, message: The content.
* asHtml: `true` to send the message as HTML.

#### S3Action
Reads, writes or deletes a text file in S3, or in a service that works like it, such as MinIO. A file that is read becomes the output of the step.

##### Variables
* accessKeyId, secretAccessKey: The credentials.
* region: Such as `eu-north-1`.
* operation: `read`, `write` or `delete`.
* bucket: The bucket.
* key: The path of the file in the bucket.
* content: The text to write.
* baseUrl: Only set this for a service that works like S3.

#### OpenAIAction
Asks OpenAI, or a server of your own that works like it. The whole answer from the server is the output of the step, as JSON.

##### Variables
* apiKey: Leave it empty if your own server does not ask for one.
* model: Such as `gpt-5`.
* userInput: What the model is asked.
* baseUrl: Only change this for a server of your own. Its certificate has to be valid.

#### TeamsAction
Sends a message to a Microsoft Teams channel through a webhook, which the Workflows app makes.

##### Variables
* webhookUrl: In a credential.
* message: The text to send.

#### MattermostAction
Sends a message to a channel on your Mattermost server. The next step can read the id of the message from `$lastLog.outputVariables.messageId`.

##### Variables
* siteUrl: The address of the server.
* token: The token of a bot account that is a member of the channel.
* channelId: View Info on the channel shows it.
* message: The text to send.
* replyToMessageId: Optional. The id of a message, to answer in its thread.

#### TwilioAction
Sends an SMS through Twilio. The next step can read the id of the message from `$lastLog.outputVariables.messageId`.

##### Variables
* accountSid, token: The Twilio console shows them.
* sender: A number you have in Twilio.
* to: With the country code, such as `+46701234567`.
* message: Up to 1600 characters.
* baseUrl: Optional.

#### GitLabAction
Creates, comments on or closes an issue, on gitlab.com or your own server. The next step can read `$lastLog.outputVariables.issueNumber` and `url`.

##### Variables
* token: An access token with the api scope.
* project: Written as `group/name`.
* operation: `createIssue`, `addComment` or `closeIssue`.
* issueNumber: Required to comment or close.
* title, issueDescription: Used to create.
* comment: Required to comment.
* baseUrl: The address of your own GitLab.

#### LinearAction
Creates or comments on an issue. The next step can read `$lastLog.outputVariables.issueKey` and `url`.

##### Variables
* token: An API key.
* operation: `createIssue` or `addComment`.
* team: The key of the team, such as `ENG`. Used to create.
* title, issueDescription: Used to create.
* issueKey: Such as `ENG-12`. Used to comment.
* comment: Required to comment.
* baseUrl: Optional.

#### ZendeskAction
Creates, comments on or solves a ticket. A comment is an internal note unless `visibleToCustomer` is set. The next step can read `$lastLog.outputVariables.ticketNumber` and `url`.

##### Variables
* siteUrl: Such as `https://acme.zendesk.com`.
* email, token: The email of an agent and an API token.
* operation: `createTicket`, `addComment` or `solveTicket`.
* ticketNumber: Required to comment or solve.
* subject, ticketDescription: Required to create.
* comment: Required to comment.
* visibleToCustomer: `true` to show the comment to the customer.

#### NotionAction
Creates a page, or adds text to one. The next step can read `$lastLog.outputVariables.pageId` and `url`.

##### Variables
* token: From an integration that the page is shared with.
* operation: `createPage` or `addText`.
* page: The address or the id. A new page is placed under this one.
* title: Required to create.
* content: Used to create. Each line becomes a paragraph.
* addedText: Required to add text. Each line becomes a paragraph.
* baseUrl: Optional.

#### GoogleSheetsAction
Reads the rows of a sheet, or adds one. The rows are given as JSON with the first row as names, such as `[{"name":"Ada"}]`, and the number of rows as `$lastLog.outputVariables.rowCount`. Values are written as text, so that a value from an earlier step cannot become a formula.

##### Variables
* serviceAccountKey: The JSON file of a service account. Share the spreadsheet with the email of that account.
* spreadsheet: The address or the id.
* sheet: The name of the tab.
* operation: `readRows` or `addRow`.
* rowValues: One value per line.
* baseUrl: Optional.

#### ClaudeAction
Asks Claude, and gives the answer as text. An answer that Claude declines, or that is cut off, fails the step. The next step can read `$lastLog.outputVariables.stopReason`, `inputTokens` and `outputTokens`.

##### Variables
* apiKey: The Claude Console gives it.
* model: `claude-opus-5-5` if nothing else is said.
* instructions: How Claude should work.
* userInput: What Claude is asked.
* effort: `low`, `medium` or `high`.
* maxTokens: The longest answer.
* baseUrl: Optional.

#### ReadEmailAction
Reads messages from a mailbox over IMAP, newest first, as JSON with `from`, `subject`, `date` and `text`. The text comes from outside, so treat it as data when a later step or an agent acts on it. The next step can read `$lastLog.outputVariables.messageCount`.

##### Variables
* hostname, port, security: The mail server. The port is 993 with `tls`.
* username, password: The mailbox.
* folder: `INBOX` if nothing else is said.
* onlyUnread: Only messages that are not read.
* markAsRead: So that the next run does not read them again.
* maxMessages: The most messages to read.

#### SlackAction
Sends a message to a Slack channel. The bot needs the `chat:write` permission and has to be a member of the channel. The next step can read the id of the message from `$lastLog.outputVariables.messageId`, for example to reply in its thread.

##### Variables
* token: A bot token that starts with `xoxb-`.
* channel: Such as `#deploys` or a channel id.
* message
* replyToMessageId: Optional. The id of a message, to answer in its thread.
* baseUrl: Optional. Only change this to go through a proxy.

#### MySQLAction
The output is JSON in the form `[{"query": "...", "rows": [{"columns": [{"name": "...", "value": "..."}]}]}]`.

Executes a MySQL statement, like a SELECT statement.
##### Variables
* hostname: hostname to connect to
* port (Optional)
* username
* password
* database
* timezone
* command

#### IfAction
##### Variables
* condition: Velocity condition. Example: $variable == "sometext".

The first OUTPUT port is the true path and the second OUTPUT port is the false path. If the chosen path is not connected, the run ends.

#### TimedWait
##### Variables
* seconds: Amount of seconds to wait

#### LinkedTaskAction
##### Variables
* name: name of another task
* parameters: a list of parameters to pass to the other task, like `[{"parameterName": "city", "parameterValue": "$parameters.get(\"city\")"}]`

#### BrowseWebAction
Starts a browser and executes a list of UI Actions.
##### Variables
* url: URL to navigate to.
* headless: If set to false, it will show the web browser window. Default is true.
* actionList: A list of actions to perform.

A UI Action contains the following fields:

* fieldName: Name of the field to control. Not required when using the actions WAIT OR ACCEPT_ALERT.
* findByType: Determines how to find the field. Possible values: ID, NAME, XPATH, CSS. Not required when using the actions WAIT OR ACCEPT_ALERT.
* action: Action to perform.

| Action        |Description                   |
| ------------- |------------------------------|
|PRESS          |Clicks on the requested field.|
|WRITE          |Writes in the requested field. Useful if the field is an input text or textarea.|
|SELECT         |Selects an item from the requested select list / dropdown based on name. |
|WAIT           |Waits a certain amount of time. Amount of time is expressed in milliseconds.|
|ACCEPT_ALERT   |Closes an alert / confirm box.|

* value: Required only if used with the actions WRITE, SELECT and WAIT.

#### MouseAction
Provides the following actions: click, move, press and release the mouse button.
##### Variables
* action: CLICK, MOVE, PRESS and RELEASE
* positionX: X position of the mouse. (MOVE action only)
* positionY: Y position of the mouse. (MOVE action only)
* button: (Default LEFT) LEFT, RIGHT

#### ReadFileAction
Reads a file from disk.
##### Variables
* filename: name of a file. Using ~ in the beginning of the filename will point to the home directory eg. ~/Downloads/testfile.txt
* parameterName: The name of the parameter to store the contents in.

#### WriteFileAction
##### Variables
* filename: name of a file. Using ~ in the beginning of the filename will point to the home directory eg. ~/Downloads/testfile.txt
* outputType: The type of the output. Possible values: clearText, binaryVar
* contents: the contents to write in the file.

#### XMLAction
##### Variables
* content: XML or JSON input.
* type: JSON_TO_XML or XML_TO_JSON.

#### SetVariableAction
Sets or modifies a variable for the current execution. A variable is read with `$parameters.get("name")`.
##### Variables
* setVariableList: A list of variables, like `[{"name": "city", "value": "Oslo"}]`. Only `value` is evaluated.

#### SwitchAction
Routes the flow by comparing a value with a list of cases.
##### Variables
* value: The value to compare.
* switchCases: A list of cases, like `[{"currentCase": "high"}, {"currentCase": "low"}]`.

The first OUTPUT ports match the cases in order. One more OUTPUT port after the last case is the default path, which is used when no case matches. Without it, a value that matches no case fails the run. With `"scriptLanguage": "JAVASCRIPT"` the value is treated as text, so compute it first with SetVariableAction and read it with Velocity.

#### ExecuteCommandAction
Executes a command on the current machine.
##### Variables
* command: Command to execute.

#### ScriptAction
Run Javascript or Velocity script
##### Variables
* type: JAVASCRIPT or VELOCITY
* script: Code to execute

#### HubSpotAction
Saves or finds a HubSpot contact, or creates a deal. The next step can read `$lastLog.outputVariables.contactId`, `$lastLog.outputVariables.found`, `$lastLog.outputVariables.dealId`.

##### Variables
* operation: `saveContact`, `findContact`, `createDeal`.
* token: Access token. From a private app in HubSpot.
* email: Email. A contact with this email is updated, and otherwise created.
* firstName: First name.
* lastName: Last name.
* dealName: Deal name.
* amount: Amount.
* dealStage: Deal stage. The internal name of the stage, such as appointmentscheduled.

#### SalesforceAction
Creates or updates a Salesforce record, or runs a query. The next step can read `$lastLog.outputVariables.recordId`, `$lastLog.outputVariables.url`, `$lastLog.outputVariables.recordCount`.

##### Variables
* operation: `createRecord`, `updateRecord`, `runQuery`.
* siteUrl: My Domain address. Such as https://acme.my.salesforce.com.
* clientId: Consumer key. From a connected app with the client credentials flow.
* clientSecret: Consumer secret.
* objectName: Object. Such as Contact, Lead or Case.
* recordId: Record id. A step that creates a record gives it as $lastLog.outputVariables.recordId.
* recordFields: Fields. As JSON, such as {"LastName": "Lovelace"}.
* query: Query. Such as SELECT Id, Name FROM Contact LIMIT 10.

#### PipedriveAction
Creates a Pipedrive person or deal, or adds a note to a deal. The next step can read `$lastLog.outputVariables.personId`, `$lastLog.outputVariables.dealId`.

##### Variables
* operation: `createPerson`, `createDeal`, `addNote`.
* siteUrl: Company address. Such as https://acme.pipedrive.com.
* token: API token. Under Personal preferences, API.
* personName: Name.
* email: Email.
* dealTitle: Title.
* dealValue: Value.
* personId: Person id. A step that creates a person gives it as $lastLog.outputVariables.personId.
* dealId: Deal id. A step that creates a deal gives it as $lastLog.outputVariables.dealId.
* note: Note.

#### MailchimpAction
Adds, tags or unsubscribes a Mailchimp contact. The next step can read `$lastLog.outputVariables.contactId`.

##### Variables
* operation: `saveContact`, `tagContact`, `unsubscribeContact`.
* apiKey: API key. Under Account, Extras, API keys. It ends with where your account is, such as -us21.
* audienceId: Audience ID. Under Audience, Settings.
* email: Email.
* firstName: First name.
* lastName: Last name.
* tagName: Tag.
* baseUrl: Base URL. Only change this to go through a proxy. It is otherwise worked out from the API key.

#### StripeAction
Creates a Stripe customer or payment link, reads a payment or refunds it. The next step can read `$lastLog.outputVariables.customerId`, `$lastLog.outputVariables.url`, `$lastLog.outputVariables.paymentStatus`, `$lastLog.outputVariables.refundId`.

##### Variables
* operation: `createCustomer`, `createPaymentLink`, `readPayment`, `refundPayment`.
* secretKey: Secret key. Under Developers, API keys. A restricted key limits what this step can do.
* email: Email.
* customerName: Name.
* priceId: Price ID. Such as price_1Mo. It is shown on the product.
* quantity: Quantity.
* paymentId: Payment ID. Such as pi_3Mt.
* amount: Amount. In the smallest unit, such as cents. Leave it empty to refund all of it.

#### ShopifyAction
Reads, lists or cancels Shopify orders, or finds a customer. The next step can read `$lastLog.outputVariables.orderId`, `$lastLog.outputVariables.url`, `$lastLog.outputVariables.orderCount`, `$lastLog.outputVariables.customerId`, `$lastLog.outputVariables.found`.

##### Variables
* operation: `readOrder`, `listOpenOrders`, `cancelOrder`, `findCustomer`.
* siteUrl: Store. Such as https://acme.myshopify.com.
* clientId: Client ID. From an app in the Dev Dashboard, under Settings.
* clientSecret: Client secret.
* token: Admin API access token. Instead of the client ID and secret, for a custom app that was made in the admin.
* orderNumber: Order number. The number shown on the order, such as 1001.
* email: Email.

#### WooCommerceAction
Reads or updates a WooCommerce order, or sets the stock of a product. The next step can read `$lastLog.outputVariables.orderStatus`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `readOrder`, `setOrderStatus`, `addOrderNote`, `setStock`.
* siteUrl: Site. Such as https://shop.example.com.
* consumerKey: Consumer key. Under WooCommerce, Settings, Advanced, REST API.
* consumerSecret: Consumer secret.
* orderNumber: Order number.
* orderStatus: Status. Such as processing, completed or cancelled.
* note: Note.
* visibleToCustomer: Visible to the customer. Otherwise the note is only for the shop.
* productId: Product ID. Shown in the list of products.
* stockQuantity: Stock.

#### IntercomAction
Creates an Intercom contact, or replies to or closes a conversation. The next step can read `$lastLog.outputVariables.contactId`.

##### Variables
* operation: `createContact`, `replyToConversation`, `closeConversation`.
* token: Access token. From an app in the Developer Hub.
* email: Email.
* contactName: Name.
* conversationId: Conversation ID. The last number in the address of the conversation.
* teammateId: Teammate ID. The last number in the address of your teammate profile. The reply is sent in this name.
* comment: Reply.
* visibleToCustomer: Visible to the customer. Otherwise the reply is an internal note.

#### FreshdeskAction
Creates, notes on, replies to or solves a Freshdesk ticket. The next step can read `$lastLog.outputVariables.ticketNumber`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createTicket`, `addNote`, `replyToCustomer`, `solveTicket`.
* siteUrl: Site. Such as https://acme.freshdesk.com.
* apiKey: API key. Under Profile settings.
* ticketNumber: Ticket number. A step that creates a ticket gives it as $lastLog.outputVariables.ticketNumber.
* email: Email of the customer.
* subject: Subject.
* ticketDescription: Description.
* comment: Text.

#### WhatsAppAction
Sends a WhatsApp message or template. The next step can read `$lastLog.outputVariables.messageId`.

##### Variables
* operation: `sendMessage`, `sendTemplate`.
* token: Access token. From a system user in Meta Business settings.
* phoneNumberId: Phone number ID. Under WhatsApp, API Setup. It is not the phone number.
* to: To. The number with its country code, such as 46701234567.
* message: Message. WhatsApp only delivers this within 24 hours of the last message from this person. Use a template otherwise.
* templateName: Template. An approved template, under WhatsApp Manager, Message templates.
* templateLanguage: Language of the template.

#### AsanaAction
Creates, comments on or completes an Asana task. The next step can read `$lastLog.outputVariables.taskId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createTask`, `addComment`, `completeTask`.
* token: Personal access token. Made in the Asana developer console.
* projectId: Project. The number in the address of the project.
* taskId: Task. A step that creates a task gives it as $lastLog.outputVariables.taskId.
* title: Title.
* taskDescription: Description.
* comment: Comment.

#### TrelloAction
Creates, moves or comments on a Trello card. The next step can read `$lastLog.outputVariables.cardId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createCard`, `moveCard`, `addComment`.
* apiKey: API key. Under Power-Ups and integrations, in your own Power-Up.
* token: Token. Made from the same page, with the link next to the API key.
* listId: List ID. Add .json to the address of a board to see the id of each list.
* cardId: Card. The short code in the address of the card.
* title: Title.
* cardDescription: Description.
* comment: Comment.

#### ClickUpAction
Creates, comments on or changes the status of a ClickUp task. The next step can read `$lastLog.outputVariables.taskId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createTask`, `addComment`, `setStatus`.
* token: API token. Under Settings, Apps. It starts with pk_.
* listId: List. The last number in the address of the list.
* taskId: Task. The code at the end of the address of the task.
* title: Title.
* taskDescription: Description.
* comment: Comment.
* taskStatus: Status. As it is written in the list, such as in progress.

#### MondayAction
Creates or comments on a monday.com item, or sets one of its columns. The next step can read `$lastLog.outputVariables.itemId`.

##### Variables
* operation: `createItem`, `addComment`, `setColumn`.
* token: API token. Under your profile, Developers, My access tokens.
* boardId: Board. The number in the address of the board.
* itemId: Item. A step that creates an item gives it as $lastLog.outputVariables.itemId.
* itemName: Name.
* columnValues: Column values. As JSON, such as {"status": "Done"}.
* comment: Update.
* columnId: Column ID. Shown in the settings of the column, such as status.
* columnValue: Value.

#### AirtableAction
Reads, adds or updates Airtable records. The next step can read `$lastLog.outputVariables.recordCount`, `$lastLog.outputVariables.recordId`.

##### Variables
* operation: `readRecords`, `addRecord`, `updateRecord`.
* token: Personal access token. Made in the builder hub, with the scopes for records.
* base: Base. The address of the base, or its id that starts with app.
* tableName: Table. As it is named in the base.
* filterFormula: Filter. A formula, such as {Status} = "Open". Leave it empty for all records.
* recordId: Record id. Starts with rec.
* recordFields: Fields. As JSON, such as {"Name": "Ada"}.

#### SupabaseAction
Reads, adds or updates rows in a Supabase table. The next step can read `$lastLog.outputVariables.rowCount`.

##### Variables
* operation: `readRows`, `addRows`, `updateRows`.
* siteUrl: Project URL. Such as https://abc.supabase.co. Under Project settings, API.
* secretKey: Secret key. Under Project settings, API keys. It can read and change all data, so only store it here.
* tableName: Table.
* filter: Filter. Which rows, such as status=eq.open. Combine with &.
* rowValues: Values. As JSON, such as {"status": "done"}, or a list of these to add several.

#### GoogleDriveAction
Finds, reads, creates or shares a Google Drive file. The next step can read `$lastLog.outputVariables.fileCount`, `$lastLog.outputVariables.fileId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `findFiles`, `readTextFile`, `createTextFile`, `shareFile`.
* serviceAccountKey: Key file of the service account. The JSON file. Share a folder or a shared drive with the email of the account.
* fileName: File name. For finding, a part of the name is enough.
* fileId: File. The long code in the address of the file. Reading is for text files, and not Google Docs.
* folderId: Folder. The long code in the address of a folder in a shared drive, as a service account has no storage of its own.
* fileContent: Content.
* email: Share with. An email address.
* role: Access. reader, commenter or writer.

#### GoogleCalendarAction
Creates, lists or deletes Google Calendar events. The next step can read `$lastLog.outputVariables.eventId`, `$lastLog.outputVariables.url`, `$lastLog.outputVariables.eventCount`.

##### Variables
* operation: `createEvent`, `listEvents`, `deleteEvent`.
* serviceAccountKey: Key file of the service account. The JSON file. Share the calendar with the email of the account.
* calendar: Calendar ID. Under the settings of the calendar, such as team@example.com.
* title: Title.
* eventDescription: Description.
* startTime: Start. With its time zone, such as 2026-12-01T09:00:00+01:00.
* endTime: End. With its time zone, such as 2026-12-01T10:00:00+01:00.
* eventId: Event ID. A step that creates an event gives it as $lastLog.outputVariables.eventId.

#### ConfluenceAction
Creates, reads or updates a Confluence page. The next step can read `$lastLog.outputVariables.pageId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createPage`, `readPage`, `updatePage`.
* siteUrl: Site. Such as https://acme.atlassian.net.
* email: Email. The email of the Atlassian account.
* token: API token. Made under the security settings of the Atlassian account.
* spaceKey: Space. The short key in the address of the space, such as ENG.
* pageId: Page. The number in the address of the page.
* parentPageId: Under the page. The number in the address of a page. Otherwise it goes directly in the space.
* title: Title. When updating, leave it empty to keep it.
* pageContent: Content. In the storage format of Confluence, such as <p>Hello</p>.

#### MongoDBAction
Finds, adds, updates or deletes MongoDB documents. The next step can read `$lastLog.outputVariables.documentCount`, `$lastLog.outputVariables.documentId`.

##### Variables
* operation: `findDocuments`, `addDocument`, `updateDocuments`, `deleteDocuments`.
* connectionString: Connection string. Such as mongodb+srv://user:password@cluster.example.net.
* database: Database.
* collection: Collection.
* filter: Filter. Which documents, as JSON, such as {"status": "open"}.
* document: Document. As JSON.
* changes: Changes. As JSON, such as {"$set": {"status": "done"}}.

#### KafkaAction
Sends a message to a Kafka topic or reads messages from one. The next step can read `$lastLog.outputVariables.messageCount`, `$lastLog.outputVariables.partition`, `$lastLog.outputVariables.offset`.

##### Variables
* operation: `sendMessage`, `readMessages`.
* username: Username. Leave empty when the cluster has no sign-in.
* password: Password.
* servers: Servers. Such as broker1.example.com:9092, broker2.example.com:9092.
* authMechanism: Sign-in method. PLAIN, SCRAM-SHA-256 or SCRAM-SHA-512.
* useTls: Use TLS.
* topic: Topic.
* messageKey: Key. Messages with the same key stay in order.
* messageBody: Message.
* consumerGroup: Consumer group. Kafka remembers per group what has been read.
* messageLimit: Most messages to read.

#### RabbitMQAction
Sends a message through RabbitMQ or reads messages from a queue. The next step can read `$lastLog.outputVariables.messageCount`.

##### Variables
* operation: `sendMessage`, `readMessages`.
* username: Username.
* password: Password.
* hostname: Hostname.
* port: Port.
* virtualHost: Virtual host.
* useTls: Use TLS.
* exchange: Exchange. Leave empty to send straight to the queue named as routing key.
* routingKey: Routing key.
* messageBody: Message.
* queueName: Queue. A message that is read is taken off the queue.
* messageLimit: Most messages to read.

#### MqttAction
Publishes an MQTT message or waits for one on a topic. The next step can read `$lastLog.outputVariables.received`.

##### Variables
* operation: `publishMessage`, `waitForMessage`.
* username: Username. Leave empty when the broker has no sign-in.
* password: Password.
* brokerUrl: Broker address. Such as tcp://broker.example.com:1883, or ssl:// for TLS.
* topic: Topic. Such as sensors/hall/temperature.
* messageBody: Message.
* quality: Quality of service. 0 is at most once, 1 at least once and 2 exactly once.
* retain: Keep for new subscribers.
* secondsToWait: Seconds to wait.

#### ElasticsearchAction
Adds, searches or deletes documents in Elasticsearch. The next step can read `$lastLog.outputVariables.hitCount`, `$lastLog.outputVariables.documentId`.

##### Variables
* operation: `search`, `addDocument`, `deleteDocument`.
* apiKey: API key. Leave empty when the cluster has no sign-in.
* siteUrl: Cluster address. Such as https://search.example.com:9200.
* indexName: Index.
* query: Query. As JSON, such as {"match": {"status": "open"}}. Empty finds everything.
* documentId: Document ID. Leave empty to let Elasticsearch choose one.
* document: Document. As JSON.

#### PrometheusAction
Runs a Prometheus query or lists the active alerts. The next step can read `$lastLog.outputVariables.resultCount`, `$lastLog.outputVariables.alertCount`.

##### Variables
* operation: `runQuery`, `listAlerts`.
* token: Bearer token. Leave empty when the server has no sign-in.
* siteUrl: Server address. Such as https://prometheus.example.com.
* query: PromQL query. Such as up == 0.

#### GrafanaAction
Marks an event on Grafana graphs, finds dashboards or lists firing alerts. The next step can read `$lastLog.outputVariables.annotationId`, `$lastLog.outputVariables.dashboardCount`, `$lastLog.outputVariables.alertCount`.

##### Variables
* operation: `addAnnotation`, `findDashboards`, `listFiringAlerts`.
* token: Service account token. Under Administration, Users and access, Service accounts.
* siteUrl: Grafana address. Such as https://grafana.example.com.
* annotationText: What happened.
* annotationTags: Tags. Separated by commas.
* dashboardUid: Dashboard UID. Leave empty to mark it on every graph.
* searchText: Search for.

#### DatadogAction
Posts a Datadog event, reads a monitor or pauses its alerts. The next step can read `$lastLog.outputVariables.eventId`, `$lastLog.outputVariables.monitorState`, `$lastLog.outputVariables.downtimeId`.

##### Variables
* operation: `postEvent`, `readMonitor`, `pauseMonitor`.
* apiKey: API key. Under Organization settings, API keys.
* applicationKey: Application key. Under Organization settings, Application keys.
* title: Title.
* eventText: Text.
* eventTags: Tags. Separated by commas, such as env:production.
* monitorId: Monitor ID. The number in the address of the monitor.
* reason: Reason.
* baseUrl: Datadog site. Such as https://api.datadoghq.eu in Europe.

#### SentryAction
Lists or reads Sentry issues, or marks one as resolved. The next step can read `$lastLog.outputVariables.issueCount`, `$lastLog.outputVariables.issueStatus`.

##### Variables
* operation: `listIssues`, `readIssue`, `resolveIssue`.
* token: Auth token. Under Settings, Developer settings, Personal tokens.
* organization: Organization slug. Shown in the address, as sentry.io/organizations/acme.
* searchText: Search for.
* issueId: Issue ID.
* baseUrl: Sentry address. Only change this for a self-hosted Sentry.

#### CloudflareAction
Purges the Cloudflare cache or changes DNS records. The next step can read `$lastLog.outputVariables.recordId`.

##### Variables
* operation: `purgeAddresses`, `purgeEverything`, `addDnsRecord`, `deleteDnsRecord`.
* token: API token. Under My Profile, API Tokens.
* zoneId: Zone ID. On the overview page of the domain.
* addresses: Addresses. One on each line.
* recordType: Type. Such as A, AAAA, CNAME or TXT.
* recordName: Name. Such as www.example.com.
* recordContent: Content. Such as 192.0.2.1.
* recordId: Record ID.
* baseUrl: Base URL. Only change this to go through a proxy.

#### DigitalOceanAction
Lists, reads, restarts or powers DigitalOcean servers on and off. The next step can read `$lastLog.outputVariables.dropletCount`, `$lastLog.outputVariables.dropletStatus`, `$lastLog.outputVariables.actionId`.

##### Variables
* operation: `listDroplets`, `readDroplet`, `rebootDroplet`, `powerOffDroplet`, `powerOnDroplet`.
* token: Personal access token. Under API, Tokens.
* tagName: Tag. Leave empty for all droplets.
* dropletId: Droplet ID. The number in the address of the droplet.

#### OktaAction
Finds an Okta user, or suspends, restores or deactivates one. The next step can read `$lastLog.outputVariables.userId`, `$lastLog.outputVariables.userStatus`.

##### Variables
* operation: `findUser`, `suspendUser`, `restoreUser`, `deactivateUser`.
* token: API token. Under Security, API, Tokens.
* siteUrl: Okta address. Such as https://acme.okta.com.
* userLogin: Login or user ID. Such as ada@example.com.

#### GiteaAction
Creates, comments on or closes Gitea issues, or opens a pull request. The next step can read `$lastLog.outputVariables.number`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createIssue`, `addComment`, `closeIssue`, `createPullRequest`.
* token: Access token. Under Settings, Applications.
* siteUrl: Gitea address. Such as https://git.example.com.
* owner: Owner.
* repository: Repository.
* issueNumber: Issue number.
* title: Title.
* issueDescription: Description.
* comment: Comment.
* sourceBranch: From branch.
* targetBranch: Into branch.

#### BitbucketAction
Opens, comments on, approves or merges a Bitbucket pull request. The next step can read `$lastLog.outputVariables.pullRequestId`, `$lastLog.outputVariables.pullRequestState`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `createPullRequest`, `addComment`, `approvePullRequest`, `mergePullRequest`.
* email: Atlassian email.
* token: API token. At id.atlassian.com, Security, API tokens.
* workspace: Workspace.
* repository: Repository.
* pullRequestId: Pull request number.
* title: Title.
* pullRequestDescription: Description.
* sourceBranch: From branch.
* targetBranch: Into branch.
* comment: Comment.
* baseUrl: Base URL. Only change this to go through a proxy.

#### CircleCIAction
Starts or reads a CircleCI pipeline, or reruns or cancels a workflow. The next step can read `$lastLog.outputVariables.pipelineId`, `$lastLog.outputVariables.pipelineNumber`, `$lastLog.outputVariables.pipelineState`.

##### Variables
* operation: `startPipeline`, `readPipeline`, `rerunWorkflow`, `cancelWorkflow`.
* token: Personal API token. Under User settings, Personal API Tokens.
* projectSlug: Project slug. Such as gh/acme/shop, under Project settings, Overview.
* branch: Branch. Leave empty for the default branch.
* pipelineId: Pipeline ID.
* workflowId: Workflow ID.
* baseUrl: Base URL. Only change this to go through a proxy.

#### WordPressAction
Writes, reads, changes or lists WordPress posts. The next step can read `$lastLog.outputVariables.postId`, `$lastLog.outputVariables.postStatus`, `$lastLog.outputVariables.url`, `$lastLog.outputVariables.postCount`.

##### Variables
* operation: `createPost`, `readPost`, `updatePost`, `listPosts`.
* username: Username.
* applicationPassword: Application password. Under Users, Profile, Application Passwords.
* siteUrl: Site. Such as https://blog.example.com.
* postId: Post ID.
* title: Title.
* postContent: Content. HTML is allowed.
* postStatus: Status. draft, pending or publish. A draft stays hidden until someone publishes it.
* searchText: Search for.

#### BoxAction
Finds and reads Box files, creates folders or shares a file. The next step can read `$lastLog.outputVariables.fileCount`, `$lastLog.outputVariables.folderId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `findFiles`, `readTextFile`, `createFolder`, `shareFile`.
* token: Access token. From an app in the Box Developer Console.
* searchText: Search for.
* fileId: File ID. The number in the address of the file.
* folderName: Folder name.
* parentFolderId: Inside folder ID. 0 is the top folder.
* sharedAccess: Who can open the link. open, company or collaborators.
* baseUrl: Base URL. Only change this to go through a proxy.

#### OneDriveAction
Finds, reads or saves text files in the OneDrive of a person. The next step can read `$lastLog.outputVariables.fileCount`, `$lastLog.outputVariables.fileId`, `$lastLog.outputVariables.url`.

##### Variables
* operation: `findFiles`, `readTextFile`, `saveTextFile`.
* tenantId: Directory (tenant) ID. On the overview page of the app registration.
* clientId: Application (client) ID.
* clientSecret: Client secret. Under Certificates and secrets.
* person: Person. The email of the person whose OneDrive it is.
* searchText: Search for.
* fileId: File ID.
* filePath: Path. Such as reports/week-45.txt. A file that is there already is replaced.
* fileContent: Content.
* baseUrl: Base URL. Only change this to go through a proxy.
* signInUrl: Sign-in URL. Only change this to go through a proxy.

#### OutlookAction
Sends email from an Outlook mailbox, lists unread email or marks it as read. The next step can read `$lastLog.outputVariables.emailCount`.

##### Variables
* operation: `sendEmail`, `listUnread`, `markAsRead`.
* tenantId: Directory (tenant) ID. On the overview page of the app registration.
* clientId: Application (client) ID.
* clientSecret: Client secret. Under Certificates and secrets.
* mailbox: Mailbox. The email address that it is sent from or read in.
* toAddresses: To. Separated by commas.
* subject: Subject.
* emailBody: Message.
* isHtml: The message is HTML.
* messageId: Email ID.
* baseUrl: Base URL. Only change this to go through a proxy.
* signInUrl: Sign-in URL. Only change this to go through a proxy.

#### SendGridAction
Sends an email through SendGrid. The next step can read `$lastLog.outputVariables.messageId`.

##### Variables
* apiKey: API key. Under Settings, API Keys.
* fromAddress: From. An address or domain that is verified in SendGrid.
* toAddresses: To. Separated by commas.
* subject: Subject.
* emailBody: Message.
* isHtml: The message is HTML.

#### PayPalAction
Creates, reads or captures a PayPal order, or refunds a payment. The next step can read `$lastLog.outputVariables.orderId`, `$lastLog.outputVariables.orderStatus`, `$lastLog.outputVariables.approvalUrl`, `$lastLog.outputVariables.captureId`, `$lastLog.outputVariables.refundId`.

##### Variables
* operation: `createOrder`, `readOrder`, `captureOrder`, `refundPayment`.
* clientId: Client ID. From an app under Apps and Credentials.
* clientSecret: Secret.
* amount: Amount. Such as 19.90. When refunding, empty means all of it.
* currency: Currency.
* orderDescription: Description.
* orderId: Order ID.
* captureId: Capture ID. The step that took the payment gives this.
* baseUrl: PayPal address. https://api-m.sandbox.paypal.com to try it without real money.

### Retry
All actions have retry mechanisms to allow you to retry an action.
#### Variables
* continueOnFailure: (Boolean) true or false. Lets the action continue on failure, ignoring any retry.
* failOn: The current action will fail if this evaluates to anything.
* retries: (Integer) Amount of retries.
* retryWait: Milliseconds to wait between retries.

### Webhooks
Besides actions, there is the webhooks nodes which can be added to the Task to start the task from a REST call.

Call the webhook using the following URL with a POST method:
http://localhost:8090/webhook/taskName/triggerName/triggerSuffix

or with path:
http://localhost:8090/webhook/taskName/triggerName/triggerSuffix/path

A task name containing a forward slash must be URL-encoded: `folder/task` becomes `folder%2Ftask`. The Edit action dialog shows the encoded URL.

* taskName: Name of the task to run
* triggerName: Name of the webhook trigger, like Webhook.
* triggerSuffix: A string that makes every webhook unique. It is set in the `triggerSuffix` field of the Webhook action.
* path: The specific path for the desired webhook.
* method: HTTP Method which this webhook will respond to.

#### Webhook
This webhook will respond to a REST call and start the flow. The body of the call is available as `$parameters.get("TRIGGER_body")`. Send it with a `Content-Type` such as `application/json`, since a form-encoded body is changed on the way.

A webhook only starts the flow when the task is active. To require credentials, set `authentication`:
* `none`: No credentials.
* `bearer`: The call must have the header `Authorization: Bearer <token>`, where `token` is set on the action.
* `basic`: The call must use basic authentication with `username` and `password` set on the action.
* `header`: The call must have the header `headerName` with the value `headerValue`, both set on the action.

A call that is rejected ends as a failed execution and does not continue the flow.

#### IntervalTrigger
Starts the flow at an interval, while the task has `"active": true`. A trigger is an action next to the StartAction, and its OUTPUT port connects to the first action to run:

```json
{
  "name": "ping_every_minute",
  "active": true,
  "actions": [
    {"@type": "StartAction"},
    {"@type": "IntervalTrigger", "time": "1", "timeUnit": "MINUTES", "ports": [{"externalId": "timer_out", "portType": "OUTPUT"}]},
    {"@type": "HttpAction", "url": "https://example.com/ping", "method": "GET", "ports": [{"externalId": "ping_in", "portType": "INPUT"}]}
  ],
  "connections": [
    {"source": {"externalId": "timer_out"}, "target": {"externalId": "ping_in"}}
  ]
}
```
##### Variables
* time: The length of the interval.
* timeUnit: SECONDS, MINUTES, HOURS or DAYS.

## Important variables

* $lastLog.output / lastLog.getOutput(): Gets the last actions output. Every action replaces it, also an IfAction, whose output is `true` or `false`. To use a value later, save it with SetVariableAction first.
* $lastLog.outputVariables / lastLog.getOutputVariables(): Gets the extra values of the last action, like `statusCode` from HttpAction. The editor lists what the step before gives under Data you can use, and agents find it under `outputs` in the step type.
* $parameters.get("paramname") / parameters.get("paramname"): Retrieves a stored parameter value. All Task paramters are stored here. You can also get the byte array of the parameter by adding .getParamValue() to the end of the statement.
* $logs / logs: A list of all task action logs. Can be traversed to look up specific values.
* $executionId / executionId: It's the ID of the current execution.
* When a queue trigger starts a task, the message can be accessed using `$parameters.get("TRIGGER_Message")`.
* When a webhook is called from an HTTP call, the headers can be accessed using the following syntax: $parameters.get("TRIGGER_*headername*"). Example: $parameters.get("TRIGGER_authorization"). The body of the request can be accessed using $parameters.get("TRIGGER_body").

## Let an agent build tasks
DopplerTask speaks the Model Context Protocol on `/mcp`, so an agent can list step types, propose tasks, run approved tasks and read the results. It needs no setup. For Claude Code:

```claude mcp add --transport http dopplertask http://localhost:8090/mcp```

If the server has tokens, add `--header "Authorization: Bearer your-agent-token"`.

What an agent saves appears in the editor as a proposal. Nothing runs until you have approved it, and you see what changed before you do.

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

The variables listed for a step below that are in a credential, such as `token`, `password` or `apiKey`, are marked as such in `GET /task/actions`, with the category `SECRET` or `CREDENTIAL`. If a task is saved with such a value written in the step, as in a task from an earlier version, the value becomes a credential. An agent is refused instead. An HTTP step's `Authorization` or key header moves the same way, and the HTTP step has one such header in a credential.

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

## Contribution
If you would like to contribute to this project, you can create pull requests, open issues and features and join discussions.
Our mission is to great open-source software to everyone.
The basic node in each task is an action. Our focus will be partially on improving the software and the other part will be to expand the library of actions.

## Authors
* **Feras Wilson**

## License

This project is licensed under the MIT license - see the [LICENSE](LICENSE) file for details