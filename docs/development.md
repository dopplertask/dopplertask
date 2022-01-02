# Development

Do you want to contribute and add a feature? Are you missing node? Have you found a bug? 
You can easily help improve DopplerTask by create a PR on the github project or report the bug in the issues tab.

## Developing locally

### Prerequisites
Install JDK 11 and gradle.

To run the backend, go to backend and run:
```gradle clean build bootRun```

To compile and run the cli:
```cd cli && go build . && ./cli tasks```

### Adding an action

All actions reside under ```backend/src/main/java/com/dopplertask/dopplertask/domain/action```

We prefer the usage of kotlin for developing tasks as it contributes to much less boilerplate code.