# Development

Do you want to contribute and add a feature? Are you missing node? Have you found a bug? 
You can easily help improve DopplerTask by create a PR on the github project or report the bug in the issues tab.

## Developing locally

### Prerequisites
Install JDK 25. The Gradle wrapper in `backend` takes care of the rest.

To run the backend, go to backend and run:
```./gradlew clean build bootRun```

To compile and run the cli:
```cd cli && go build . && ./cli tasks```

### Adding an action

All actions reside under ```backend/src/main/java/com/dopplertask/dopplertask/domain/action```

We prefer the usage of kotlin for developing tasks as it contributes to much less boilerplate code.

### Adding an icon

A step names its icon in `PRESENTATION` in `engine.js`. A service with a logo gets it in `js/flow/brand-icons.js`, where each logo is centred on its own square and fills 92% of it, so that all of them are the same size. Take a square version of the logo from a set with a licence that allows it, and read the licence of the icon itself, since a set can hold icons under other licences.

## Logos

The logos in the editor come from [Simple Icons](https://github.com/simple-icons/simple-icons) and [gilbarbara/logos](https://github.com/gilbarbara/logos), which are both released under CC0-1.0, apart from these:

- The Jenkins logo is by the Jenkins project, under [CC-BY-SA-3.0](https://creativecommons.org/licenses/by-sa/3.0/).
- The Apache Kafka logo is by the Apache Software Foundation, under [Apache-2.0](https://www.apache.org/licenses/LICENSE-2.0).

They are only used to show which service a step connects to, and they remain trademarks of their owners. A step without a logo here, such as Outlook, Freshdesk and Pipedrive, has a general icon since no set with an open licence holds one.
