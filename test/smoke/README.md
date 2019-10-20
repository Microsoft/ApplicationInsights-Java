# ApplicationInsights-Java Smoke Tests

## Prerequisites
* Windows 10
* [Docker for Windows][windock]
* Set environment variables:
    * **DOCKER_EXE** This should point to docker.exe, e.g. C:\Program Files\Docker\Docker\resources\bin\docker.exe
    * **DOCKER_CLI_EXE** This should point to DockerCli.exe, e.g. C:\Program Files\Docker\Docker\DockerCli.exe


## Overview
The goal for the smoke tests is to exercise and validate the ApplicationInsights Java SDK in each supported environment. The test matrix has dimensions for OS, Application Servers and JREs. A custom task in the gradle build script is used for generating the application environments (using Docker), building the test applications and running the tests against the applications in each environment.

### Directory Structure
All directories are relative to the _smoke test basedir_: `ApplicationInsights-Java/test/smoke/`
* `/testApps`
    * The subdirectories here contain the test application code and the tests against that application. Each directory should be named for the test application. The test application code should generate a WAR file for deployment and the smokeTest code should
    // TODO link to details about writing test applications and tests

* `/appServers`
    * This contains scripts and Dockerfiles for running the application servers. Each application server should be in a directory named for its app server. The Dockerfile(s) for each environment are parameterized. These are generated by the `.partial.dockerfile` files in each application servers' directory.
* `/utils`
    * This is a key dependency of the test code under `/testApps`. It contains classes which simplify writing tests.

## Running the Smoke Tests
To run the smoke tests, use the following command:
```
gradle smokeTest
```

This will build the SDK, then build the test environments' docker containers, then build the test applications and their tests; and finally run the tests with the test application deployed to each container.

To see exactly what is being run, try Gradle's "dry run" option: `gradlew -m smokeTest`.

## Execution
The `smokeTest` task performs the following in order:
1. Assembles the SDK
2. Build the test applications (depends on SDK)
3. Generate dockerfiles for application server environments (the `generateDockerfiles` task)
4. Build the docker images from dockerfiles (depends on dockerfile generation; the `buildDockerImages` task)
5. Compile test code
6. Runs the tests

// TODO path to reports

# How to...
## Configure supported JREs for application servers
There are three different files used for determining the supported JREs of an applciation server: the master file, the excludes file, and the includes file.

Each of these files has the same format, one image name per line.

Each line in these files are a reference to a docker image which can be found in the [Docker Hub][dhub]. When the **generateDockerfiles** task is run, a file is generated for each line in this file.

It is recommended for an application server to have either an includes or excludes file, or neither; but not both.

### Specify JREs for all application servers
To add a JRE for all application servers to use, simply add the name of the docker image of the JRE to the master JRE file: `/test/smoke/appServers/jre.master.txt`.

All application servers will generate a dockerfile with these base images unless an excludes file or includes file exists for it.

### Exclude JREs for specific applications servers
If an excludes file exists, e.g. `/test/smoke/appServers/`*`MyAppServer`*`/jre.excludes.txt`, then these will not be used for *MyAppServer*.

Images in the excludes file which are missing from the master file will have no effect on compilation, but will generate a warning.

### Explicitly specify the JREs for an application server
If an includes file exists, e.g. `/test/smoke/appServers/`*`MyAppServer`*`/jre.includes.txt`, then *only* these will be used for *MyAppServer*.

This file can include JREs which are not found in the master file.

## Add a test application
1. Create a test application directory, e.g. `/test/smoke/testApps/`**_`MyTestApp`_**
2. Create the application's build file: `/test/smoke/testApps/`_`MyTestApp/`**`build.gradle`**_
    * This can be used just like any other build file: add dependencies, add custom build step, add unit tests, etc. See the [gradle documantation][gradledocs] for more information.
    * The application source should be in the standard location: `/test/smoke/testApps/`_`MyTestApp`_**`/src/main/java`**
    * You will add directories for smoke tests in the next section.
3. Finally, add this project to the root project, e.g. add the following to `ApplciationInsights-Java/settings.gradle`:
    ```gradle
    include ':test:smoke:testApps:MyTestApp'
    ```

Note: your test application should have a "health check" endpoint at the root. For example, if your application is deployed with the root context `/MyApp` then `/MyApp/` should return a 200 response with a non-empty body when the application is healthy. This is used to determine when the application is successfully deployed. The body content is arbitrary, but must be non-empty.

### Add a test case/class
In the current system, test cases are coupled with the test application. So, you must have created a test application in the previous section to create a test case.

1. Create the smoke test source and resource directories:
    * `/test/smoke/testApps/`_`MyTestApp`_**`/src/smokeTest/java`**
    * `/test/smoke/testApps/`_`MyTestApp`_**`/src/smokeTest/resources`**

2. In the smoke test resource directory, create a file `appServers.txt`.
    * You must explicitly specify the shortnames of the application servers where the test application should be deployed; one per line.
    * The application server shortnames can be found in `build.gradle` in their respective directories.

3. Create a smoke test class. This is a JUnit test which inherits from `AiSmokeTest` found in `/utils`.
// TODO metion that junit and utils is already a dependency

4. Specify any additional dependencies using the configuration `smokeTestCompile`, e.g.:
    ```gradle
    dependencies {
        smokeTestCompile 'some.org:some.artifact:1.0'
    }
    ```

#### Using the agent for a test class (Optional)
The agent JAR is automatically included in each container. The start scripts will detect if the agent is to be used and update the application server configuration accordingly. The agent can only be attached at the class-level since container live for the duration of the test class.

1. Ensure the desired agent configuration file is in `/test/smoke/appServers/global-resources/`_**`configName`**_`_ApplicationInsights.json`. A default file is already included `default_ApplicationInsights.json`. The _configName_ should be alphanumeric and without underscores. This name will be used in the next step to tell the framework which config file to use.
2. Add the `@UseAgent("`_**`configName`**_`")` annotation to the test class. The parameter is the _configName_ from step 1; the prefix of the config file. For example, if the `default_ApplicationInsights.json` is to be used then the class should be annotated with `@UseAgent("default")`. If no argument is given, the default config file is used.

#### Using a depedency container in a test class (Optional)
A dependency container is an additional container which is started just before the appServer container where the test app is deployed. Examples include a database server like MySQL or a redis cache. It is intended that the test application interact with the dependency containers while under test. Just like the agent annotation, dependency containers are a class-level construct.

Any container which can be found on [hub.docker.com](dhub) can be used. Alternatively, a custom container could be built; as long as the docker client can locate the container.

1. Add the `@WithDependencyContainers` annotation to the test class.
    * This annotation requires at least one `@DependencyContainer` parameter, but can take any number of them.
    * The parameters have the following form:
    ```java
    @DependencyContainer(
        value="name_of_container",
        imageName="name_of_docker_image",
        portMapping="host_port:docker_port", // or just "port_number" if both are the same
        environmentVariable="env_variable_name_used_in_testapp"
    )
    ```
    Only `value` is required. If `imageName` and/or `environmentVariable` is missing, the value of `value` is used for those parameters as well (see note below).
2. Use the environment variable in the test application. This will contain the hostname (the value of `value`) of the dependency container to configure a client in the test application via `System.getenv`.

**NOTE:** If the `environmentVariable` parameter is given, it will be used exactly as given. If `value` is to be used for the environment variable name, it will be transformed to _UPPER_SNAKE_CASE_ to match the "normal" naming scheme for environment variables. It is reccommended that `environmentVariable` be given to avoid any issues.

For example, the two configurations shown here produce the same result:
```java
// example 1
@DependencyContainer(value="redis",portMapping="6379")
```
```java
//example 2
@DependencyContainer(value="redis",imageName="redis",portMapping="6379:6379",environmentVariable="REDIS")
```
This configuration does the following
* Creates a shared (bridge) network in docker. All containers will use this network.
* Starts the `redis` image found on [hub.docker.com](dhub) before starting the test application container.
* Passes the environment variable `REDIS=redis` to the test application container using docker's `--env` parameter.
* The test application should use `System.getenv("REDIS")` to retrieve the host name of the Redis server to configure the Java redis client.

The example outlined here can be seen in action in `/test/smoke/testApps/CachingCalculator` which uses a Redis cache to test the AI Java agent dependency telemetry collection.

## Add an application server
1. Create a subdirectory under `/test/smoke/appServers`. The subdirectory should be named the name of the app server and its version separated by a dot (`.`). For example, `/test/smoke/appServers/`_**`MyAppServer.1`**_ would represent and app server named _MyAppServer_ version _1_.

2. Inside the app server subdirectory, create a `build.gradle` which defines the variable `appServerShortName`. Generally, this is the app server's name and it's version in all lowercase with no spaces or special characters. Currently, this is the name the rest of the system will use to refer to this environment (container names, inside tests, etc.).

Example for `/test/smoke/appServers/`_**`MyAppServer.1`**_`/build.gradle`:
```gradle
ext {
    appServerShortName = 'myappserver1'
}
```

3. Create partial dockerfile with the following naming scheme: _**`appServerShortName`**_`.`_**`target_os`**_`.partial.dockerfile`. **appServerShortName** should be the same as defined in step 2. More on this in the next section.

Currently, the only **target_os** supported is `linux`. There are plans to support more in the future.

4. Inside the appserver subdirectory, create a directory named `resources` with a subdirectory for each target OS (again, currently only `linux` is supported). For example, `/test/smoke/appServers`_`MyAppServer.1`_`/`_**`resources/linux`**_

5. Inside the `resources/linux` directory, create two scripts specific to this application server:
    *  `deploy.sh` - This should take one argument, the absolute path to the test application WAR file. The script will use the application server's mechanism to deploy the WAR file into the server. This can be using the appserver's management API or copying the WAR into a scanned directory; it depends on the application server spec.
    `deploy.sh` will be run after the appserver has started and after the test app WAR is copied into the container.
    * `tailLastLog.sh` - This takes one optional argument, number of lines to tail. This will be run when a test failed and the goal is to provide any additional diagnostics for addressing the test failure. When run, it should print the given/default number of lines from the tail of the application server's logs. This should include the application server log file which captures the logs from the test application. Logs from deployment should also be included. This could be from one or more files depending on the application server spec. If more than one file is tailed, include a filename header before dumping the log file.
6. Add an `inlucde` statement to `settings.gradle` at the root of the repository. For example:
```gradle
include ':test:smoke:appServers:MyAppServer'
```
### Writing the `.partial.dockerfile`
This can be written like any other Dockerfile, with some additional steps:
1. The base image must be `@JRE@`. This is a template variable used by the system. It will be replaces by the JRE base images supported by this application server.
2. The final `WORKDIR` should contain the scripts from step 5.

For example:
```dockerfile
FROM @JRE@

# then continue writing the dockerfile as normal ...

# ... more docker commands ...

# you should include something like this (the directory name can vary):
RUN mkdir /docker-stage

ADD ./deploy.sh /docker-stage/deploy.sh
ADD ./tailLastLog.sh /docker-stage/tailLastLog.sh

# then at the end of the file...
WORKDIR /docker-stage
# the final workdir should be the directory with the scripts
```

Notes/tips:
* The actual docerfiles are generated and copied into `/test/smoke/appServers/`_`appServerName.version`_`/build/dockerfiles`. Check here if the dockerfiles are not building correctly.
* When building docker images, the system first copies the dockerfiles and resources into a staging directory: `/test/smoke/appServers/`_`appServerName.version`_`/build/tmp`. Here is another place to check when debugging docker build issues.

### Optional steps
7. If the applciation server does not support a specific JRE, you can add one of these files (see note below) to filter the JREs for a specific application server:
    * `jre.excludes.txt` - list of JREs from the master file (`/test/smoke/appServers/jre.master.txt`) which _should not_ be used as a base image for this application server.
    * `jre.includes.txt` - list of JREs which _should_ be used as base image for this applications server. This overrides `jre.master.txt`, so this file can be though of as the master file specifically for this app server. If you want to add an additional JRE, copy the contents of `jre.master.txt` into it and add the additional entry. If this app server supports only 1 JRE, then include it as the only entry in this file.

Note: Though the system supports having both of these files, it's easier to manage if you include only one or the other.

## Run specific test(s) or test class(es)
Use the `--tests ` _`<testpattern>`_ option with the `smokeTest` task. *testpattern* can use `*` as a wildcard. For example,
```
gradlew smokeTest --tests "*SomeTestClass*trackEvent*"
```
This example will run all tests in classes whose name contains *SomeTestClass* and test methods with names containing *trackEvent*.

See [gradle documentation](gradledocs) for more information.

# Future Plans
* Run smoke tests against provided SDK JARs
* Start up a application server for manual testing
* Build a docker image to be use as a base image for an application server.
* Decouple tests from test applications (common schema? dynamic resources created by the test?).
* Detect if the smokeTests are being run on a linux machine and only run the Linux tests. Same for Windows.
* gradle tasks for creating a new test application, new environment; maybe automating other things e.g. 'add jre'
* Automate discovery of application servers.
* Automate _appServerShortName_ to remove need for build.gralde only with variable name defined.

# References
* [Docker for Windows][windock]
* [Docker Hub][dhub]
* [Gradle Documentation][gradledocs]

[windock]: https://www.docker.com/docker-windows
[dhub]: https://hub.docker.com/
[gradledocs]: https://docs.gradle.org/current/userguide/userguide.html