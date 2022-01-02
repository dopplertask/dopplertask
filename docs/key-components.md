# Key Components

## Action node
An action is the main building block in every task which can do an operation. Custom actions can be added by extending the Action class.

## Trigger node
A trigger node is a node that starts the task based on a web hook call, a timer or an external event.

## Connection
A connection is a link between action (ports). Each action can have one or multiple connections.

## Task
A task is what the user executes and consists of multiple actions. A task run ends when all actions have processed their data.