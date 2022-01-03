#!/usr/bin/env node


const yargs = require("yargs");
const http = require('http');

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
            if(argv.d) {
                callBackend("/schedule/task", "POST", data, function(obj) {
                    if(obj != undefined) {
                        console.log(obj.id)
                    }
                });
            }
            else {
                callBackend("/schedule/directtask", "POST", data, function(obj) {
                    if(obj != undefined) {
                        obj.output.forEach(element => {
                            console.log(element)
                        });
                    }
                });
            }  
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
})
.strict().demandCommand().help().argv;


