#!/usr/bin/env node


const yargs = require("yargs");
const http = require('http');

const FOLLOW_POLL_MS = 250;

// Prints each step as it finishes, and ends with a failing exit code if the run failed.
function followRun(executionId, printedSteps = 0) {
    callBackend("/execution/" + executionId, "GET", null, function(execution) {
        const steps = execution.steps || [];
        steps.slice(printedSteps).forEach(step => {
            if (step.output) {
                console.log(step.output.trimEnd());
            }
            if (step.error) {
                console.error(step.error);
            }
        });

        if (execution.status === "FINISHED") {
            return;
        }
        if (execution.status === "FAILED") {
            if (steps.length === 0) {
                execution.output.forEach(line => console.error(line));
            }
            process.exitCode = 1;
            return;
        }
        setTimeout(() => followRun(executionId, steps.length), FOLLOW_POLL_MS);
    });
}

function callBackend(path, method, data, callbackResult) {

    if(data == null || data == undefined) {
        data = "";
    }
    const httpOptions = {
        hostname: 'localhost',
        port: 8090,
        path: path,
        method: method,
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': data.length
        }
    }

    const req = http.request(httpOptions, res => {

        let resultData = "";
        res.on('data', d => {
            resultData += d;
        })

        res.on("end", function () {

            const obj = JSON.parse(resultData);

            callbackResult(obj);
        })
    })

    req.on('error', error => {
        console.error(error)
    })

    req.write(data)
    req.end()

}


const options = yargs
.command({
    command: "logs [executionid]",
    describe: "See the logs of an execution",
    demandOption: true,
    handler(argv) {
        callBackend("/execution/" + argv.executionid, "GET", null, function(obj) {
            obj.output.forEach(e => {
                console.log(e)
            })
            
        });
    }
})
 .command({
    command: "run [taskname] [parameters...]",
    describe: "Run a task",
    demandOption: true,
    builder: (yargs) => yargs.option('d', {
        alias: 'detach',
      desc: 'Run task in background and print task checksum',
      type: 'boolean',
      global: true 
    }),
    handler(argv) {
   
        // Prepare parameters variable
        let taskParameters = {};

        // Read all parameters
        if(argv.parameters != undefined) {
            argv.parameters.forEach(parameter => {
                let fragmentedArray = parameter.split("=");
                taskParameters[fragmentedArray[0]] = fragmentedArray[1];
            })
        }

        // Construct final data that will be sent to the backend
        const data = JSON.stringify({
            taskName: argv.taskname,
            parameters: taskParameters
        });


        if(argv.taskname != undefined) {
            
            callBackend("/schedule/task", "POST", data, function(obj) {

                if(obj != undefined) {
                    console.log(obj.id)
                    if(argv.d == undefined) {
                        followRun(obj.id);
                    }  
                }
            });

           
        }
        else {
            console.log("Please enter a task name");
        }
    }
})
.command({
    command: "tasks",
    describe: "List all tasks",
    demandOption: true,
    handler(argv) {
        callBackend("/task", "GET", null, function(obj) {

            var out = obj.map(function (el) {
                let parameters = []
                el.parameters.forEach(parameter => {
                    console.log(parameter)
                    parameters.push(parameter.name)
                });

                return {
                  name: el.name,
                  checksum: el.checksum,
                  active: el.active,
                  created: el.created,
                  parameters: parameters
                };
              });

            console.table(out)
        });
    }
})
.command({
    command: "ps",
    describe: "List all executions",
    demandOption: true,
    handler(argv) {
        callBackend("/executions", "GET", null, function(obj) {

            var out = obj.executions.map(function (el) {

                return {
                  executionId: el.executionId,
                  taskName: el.taskName,
                  startDate: el.startDate,
                  endDate: el.endDate,
                  status: el.status
                };
              });

            console.table(out)
        });
    }
}).command({
        command: "push [taskname]",
        describe: "Push task to the DopplerTask hub",
        demandOption: true,
        handler(argv) {
            const data = JSON.stringify({
                taskName: argv.taskname,
            });
            callBackend("/task/push", "POST", data, function(e) {
                console.log(e.taskName);
            });
        }
    })
.command({
    command: "login [username] [password]",
    describe: "Login to DopplerTask hub",
    demandOption: true,
    handler(argv) {
        const data = JSON.stringify({
            username: argv.username,
            password: argv.password,
        });
        callBackend("/login", "POST", data, function(e) {
            console.log(e.message);
        });
    }
}).command({
    command: "pull [taskname]",
    describe: "Download task from DopplerTask hub",
    demandOption: true,
    handler(argv) {
        callBackend("/task/download?taskName=" + argv.taskname, "GET", null, function(e) {
            console.log(e.checksum);
        });
    }
}).command({
    command: "rm [tasknameOrChecksum]",
    describe: "Delete task from local instance by task name or checksum",
    demandOption: true,
    handler(argv) {
        callBackend("/task/" + argv.tasknameOrChecksum, "DELETE", null, function(e) {
            console.log(e.message);
        });
    }
})
.strict().demandCommand().help().argv;


