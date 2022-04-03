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

To run a task, send the JSON with the task id and parameters to the REST API:

```
{
  "taskName": "doppler-example",
  "parameters": {
  }
}
```

Example of the call:

```curl -X POST http://localhost:8090/schedule/task -H "Content-Type: application/json" -d @run_task.json```

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
* body
* headers: Key-value list of headers

#### MySQLAction
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
* pathTrue: Name of the true path.
* pathFalse: Name of the false path.

#### TimedWait
##### Variables
* seconds: Amount of seconds to wait

#### LinkedTaskAction
##### Variables
* taskName: name of another task
* parameters: a list of parameters to pass to the other task

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
Sets or modifies a variable for the current execution.
##### Variables
* setVariableList: A list of key-value pairs of variables.

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

### Webhooks
Besides actions, there is the webhooks nodes which can be added to the Task to start the task from a REST call.

Call the webhook using the following URL with a POST method:
http://localhost:8090/webhook/taskName/triggerName/triggerSuffix

or with path:
http://localhost:8090/webhook/taskName/triggerName/triggerSuffix/path

* taskName: Name of the task to run
* triggerName: Name of the webhook trigger, like Webhook.
* triggerSuffix: A random string to make every webhook unique.
* path: The specific path for the desired webhook.
* method: HTTP Method which this webhook will respond to.

#### Webhook
This webhook will respond to a REST call and start the flow.

## Important variables

* $lastLog.output / lastLog.getOutput(): Gets the last actions output
* $parameters.get("paramname") / parameters.get("paramname"): Retrieves a stored parameter value. All Task paramters are stored here. You can also get the byte array of the parameter by adding .getParamValue() to the end of the statement.
* $logs / logs: A list of all task action logs. Can be traversed to look up specific values.
* $executionId / executionId: It's the ID of the current execution.
* When a webhook is called from an HTTP call, the headers can be accessed using the following syntax: $parameters.get("TRIGGER_*headername*"). Example: $parameters.get("TRIGGER_authorization"). The body of the request can be accessed using $parameters.get("TRIGGER_body").

## Contribution
If you would like to contribute to this project, you can create pull requests, open issues and features and join discussions.
Our mission is to great open-source software to everyone.
The basic node in each task is an action. Our focus will be partially on improving the software and the other part will be to expand the library of actions.

## Authors
* **Feras Wilson**

## License

This project is licensed under the MIT license - see the [LICENSE](LICENSE) file for details