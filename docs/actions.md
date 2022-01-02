# Actions

## Common variables for all actions
* scriptLanguage: VELOCITY (default), JAVASCRIPT
* ports: Defined input and output ports for the current action. A connection object connects two ports together. The next action can be reached via a connection object.

## PrintAction
### Variables
* message: A message to be printed

## SSHAction
Connects to a machine via SSH and executes a command
### Variables
* hostname: hostname to connect to
* username
* password
* command: A command to execute once connected

## SecureCopyAction
### Variables
* hostname: hostname to connect to
* username
* password
* sourceFilename: location of the file to be transferred
* destinationFilename: location of where the file will be placed in the remote host

## HttpAction
### Variables
* url
* method: GET, POST, PUT, DELETE
* body
* headers: Key-value list of headers

## MySQLAction
Executes a MySQL statement, like a SELECT statement.
### Variables
* hostname: hostname to connect to
* port (Optional)
* username
* password
* database
* timezone
* command

## IfAction
### Variables
* condition: Velocity condition. Example: $variable == "sometext".
* pathTrue: Name of the true path.
* pathFalse: Name of the false path.

## TimedWait
### Variables
* seconds: Amount of seconds to wait

## LinkedTaskAction
### Variables
* taskName: name of another task

## BrowseWebAction
Starts a browser and executes a list of UI Actions.
### Variables
* url: URL to naviate to.
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

## MouseAction
Provides the following actions: click, move, press and release the mouse button.
### Variables
* action: CLICK, MOVE, PRESS and RELEASE
* positionX: X position of the mouse. (MOVE action only)
* positionY: Y position of the mouse. (MOVE action only)
* button: (Default LEFT) LEFT, RIGHT

## ReadFileAction
Reads a file from disk.
### Variables
* filename: name of a file. Using ~ in the beginning of the filename will point to the home directory eg. ~/Downloads/testfile.txt
* parameterName: The name of the parameter to store the contents in.

## WriteFileAction
### Variables
* filename: name of a file. Using ~ in the beginning of the filename will point to the home directory eg. ~/Downloads/testfile.txt
* contents: the contents to write in the file.

## XMLAction
### Variables
* content: XML or JSON input.
* type: JSON_TO_XML or XML_TO_JSON.

## SetVariableAction
Sets or modifies a variable for the current execution.
### Variables
* setVariableList: A list of key-value pairs of variables.

## ExecuteCommandAction
Executes a command on the current machine.
### Variables
* command: Command to execute.

## ScriptAction
Run Javascript or Velocity script
### Variables
* type: JAVASCRIPT or VELOCITY
* script: Code to execute

# Retry
All actions have retry mechanisms to allow you to retry an action.
## Variables
* continueOnFailure: (Boolean) true or false. Lets the action continue on failure, ignoring any retry.
* failOn: The current action will fail if this evaluates to anything.
* retries: (Integer) Amount of retries.

# Webhooks
Besides actions, there is the webhooks nodes which can be added to the Task to start the task from a REST call.

Call the webhook using the following URL with a POST method:
http://localhost:8090/webhook/taskName/triggerName/triggerSuffix

or with path:
http://localhost:8090/webhook/taskName/triggerName/triggerSuffix/path

* taskName: Name of the task to run
* triggerName: Name of the webhook trigger, like Webhook.
* triggerSuffix: A random string to make every webhook unique.
* path: The specific path for the desired webhook.

## Webhook
This webhook will respond to a REST call and start the flow.
