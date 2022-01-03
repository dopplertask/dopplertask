#!/usr/bin/env node


const yargs = require("yargs");
const http = require('http');

function callBackend(path, data) {


    const httpOptions = {
        hostname: 'localhost',
        port: 8090,
        path: path,
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': data.length
        }
    }

    const req = http.request(httpOptions, res => {
        console.log(`statusCode: ${res.statusCode}`)

        let resultData = "";
        res.on('data', d => {
            resultData += d;

        })

        res.on("end", function () {

            const obj = JSON.parse(resultData);

            obj.output.forEach(element => {
                console.log(element)
            });
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
    handler(argv) {
        console.log(argv)

        // Prepare parameters variable
        let taskParameters = {};

        // Read all parameters
        argv.parameters.forEach(parameter => {
            let fragmentedArray = parameter.split("=");
            taskParameters[fragmentedArray[0]] = fragmentedArray[1];
        })

        // Construct final data that will be sent to the backend
        const data = JSON.stringify({
            taskName: argv.taskname,
            parameters: taskParameters
        });

        callBackend("/schedule/directtask", data);
    }
})
.command({
    command: "tasks",
    describe: "List all tasks",
    demandOption: true,
    handler(argv) {
        console.log(`Running task: ${argv.taskname}`);
    }
}).strict().demandCommand().help().argv;


