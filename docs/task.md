# Build a task through the API

A task is a set of steps joined by connections. Most people build one in the editor or ask an agent to, and this page is for building one by hand.

## Example
### Add a task

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

### Run a task

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

### The format of a task

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

## Task parameters
Task paramters are used to indicate what initial variables are needed to run a task.
They can be either required or not required, and can also have a default value.

All task parameters are put into the $parameters variable (see Important Variables).

To add task parameters manually using JSON:
```
"parameters": [
    {"name": "testVar", "description": "This is just a test var", "required": true, "defaultValue": "testasdasdasas"}
  ],
```
