<div align="center">

![DopplerTask - Open-source Workflow Automation](https://github.com/dopplertask/dopplertask/blob/2b88c494bc774023ecd41a92b685c980fb29158f/docs/images/dopplertask_logo.png)


<h1>DopplerTask - Task Automation</h1>

DopplerTask is a revolutionary open-source software that allows you to easily automate tasks. Whether it’s a bunch of bash scripts or just starting your car remotely, you can automate it. Build, run, reuse and share automations with anyone around the globe.

On top of all of this life-simplifying project, we are striving to make an climate friendly software that is fast, easy and consumes as little resources as possible.

![DopplerTask - GUI](https://github.com/dopplertask/dopplertask/blob/ce7050284e58eb90f8b76f28d6b1214aab8471ea/docs/images/dopplertask_gui.png)
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

Install JDK 17 and gradle.

(Optional) Install CLI:
```npm install -g dopplertask-cli```

(Optional) Or to install the CLI from the cli directory, run:
```npm install -g .```

## Run
To run the backend, go to the backend folder and run:
```gradle clean build bootRun```

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

`/schedule/task` queues the run and returns its id, and `GET /execution/{id}` returns its status, logs and the result of each step. To continue a failed run at the step that failed, without repeating the steps before it, add `"resumeFromExecutionId"` with its id. Its parameters are kept unless the request gives new values. `/schedule/directtask` takes the same JSON and waits for the result, which holds `output`, `status`, `executionId`, `logs` with every step and, if the run failed, `error`. A webhook only answers with `output`, `status` and `executionId`, since it can be called by anyone, so use `GET /execution/{id}` to see why a webhook run failed.

#### Build a task through the API

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
      "failOn": "#if($result.outputVariables.statusCode >= 500)server error#end",
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

Every HTTP status counts as a success. To fail on a status, use failOn with `$result.outputVariables.statusCode`.

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
* $lastLog.outputVariables / lastLog.getOutputVariables(): Gets the extra values of the last action, like `statusCode` from HttpAction.
* $parameters.get("paramname") / parameters.get("paramname"): Retrieves a stored parameter value. All Task paramters are stored here. You can also get the byte array of the parameter by adding .getParamValue() to the end of the statement.
* $logs / logs: A list of all task action logs. Can be traversed to look up specific values.
* $executionId / executionId: It's the ID of the current execution.
* When a queue trigger starts a task, the message can be accessed using `$parameters.get("TRIGGER_Message")`.
* When a webhook is called from an HTTP call, the headers can be accessed using the following syntax: $parameters.get("TRIGGER_*headername*"). Example: $parameters.get("TRIGGER_authorization"). The body of the request can be accessed using $parameters.get("TRIGGER_body").

## Contribution
If you would like to contribute to this project, you can create pull requests, open issues and features and join discussions.
Our mission is to great open-source software to everyone.
The basic node in each task is an action. Our focus will be partially on improving the software and the other part will be to expand the library of actions.

## Authors
* **Feras Wilson**

## License

This project is licensed under the MIT license - see the [LICENSE](LICENSE) file for details