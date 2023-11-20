# Setting up Docker

Remark: adding the regular user to the `docker` group defeats all
security isolation (it makes the user a quasi-root), so all Docker
commands are run with `sudo`.

## Creating the database image

We create a "database" directory to house the configuration for our
database image.

There, we create a `Dockerfile` file instructing to create an image
starting for the Alpine Linux version of the Postgres base
image. Environment variables are provided for the configuration.

```dockerfile
FROM postgres:14.1-alpine

ENV POSTGRES_DB=db \
    POSTGRES_USER=usr \
    POSTGRES_PASSWORD=pwd
```

The image is then build, and tagged "me/tp-database":
```sh
sudo docker build -t me/tp-database database/
```

Once the image is build, a container can be started, binding it to the
port 5000 on the host machine (Postgres' default port is 5432:

```sh
sudo docker run -p 5000:5432 --name database-layer me/tp-database
```

It runs without error.

Let’s remove it since we’ll want to reuse its name afterwards for a
new instance:

```sh
sudo docker stop databse-layer
sudo docker remove database-layer
```

We now create a network to isolate containers together:
```sh
sudo docker network create app-network
```
```sh
sudo docker run -p 5000:5432 --name database-layer --network app-network me/tp-database
```

We use _Adminer_ as a client to connect to the database and view its
contents. It too is available as a Docker image:

```sh
sudo docker run -d \
     -p "8090:8080" \
     --net=app-network \
     --name=adminer \
     adminer
```

The database is currently empty --- after all, it was just created.

To initialise the database, we create the files `01-CreateScheme.sql`
and `02-InsertData.sql`. They are copied in the
`/docker-entrypoint-initdb.d` directory of the container when it is
started. The first file creates the tables "departments" and
"students"; the second file inserts some data in them.

```sql
CREATE TABLE public.departments
(
 id      SERIAL      PRIMARY KEY,
 name    VARCHAR(20) NOT NULL
);

CREATE TABLE public.students
(
 id              SERIAL      PRIMARY KEY,
 department_id   INT         NOT NULL REFERENCES departments (id),
 first_name      VARCHAR(20) NOT NULL,
 last_name       VARCHAR(20) NOT NULL
);
```
The `01-CreateScheme.sql` file.

The necessary instructions are added to the Dockerfile:

```dockerfile
FROM postgres:14.1-alpine

COPY 01-CreateScheme.sql /docker-entrypoint-initdb.d/
COPY 02-InsertData.sql /docker-entrypoint-initdb.d/

ENV POSTGRES_DB=db \
    POSTGRES_USER=usr \
    POSTGRES_PASSWORD=pwd
```

And the image is rebuilt like before.

The data is indeed inserted in the database, as seen in Figure 1:

![The database with the two tables added, in Adminer.](assets/adminer-database-filled.png)

Lastly, we add a _bind volume_ to the container for persistence:
```sh
sudo docker run -p 5000:5432 -v ./database/persistence:/var/lib/postgresql/data --name database-layer --network app-network me/tp-database
```

This means the files from the container located in the directory
`/var/lib/postgresql/data` are copied to the host, in the directory
`database/persistence`.

With the data being kept on the host’s disk, it stays between
executions, even if the container is destroyed. The data it creates looks like:

```
$ sudo ls database/persistence/
base	      pg_ident.conf  pg_serial	   pg_tblspc	postgresql.auto.conf
global	      pg_logical     pg_snapshots  pg_twophase	postgresql.conf
pg_commit_ts  pg_multixact   pg_stat	   PG_VERSION	postmaster.opts
pg_dynshmem   pg_notify      pg_stat_tmp   pg_wal
pg_hba.conf   pg_replslot    pg_subtrans   pg_xact
```

The database’s password should not be written in a Dockerfile: this
file is distributed to anyone, and should not contain any sensitive
information. Instead, environment variables are better provided with
command line options.

The cleaned-up Dockerfile is then:
```dockerfile
FROM postgres:14.1-alpine

COPY 01-CreateScheme.sql /docker-entrypoint-initdb.d/
COPY 02-InsertData.sql /docker-entrypoint-initdb.d/
```

And the command to instantiate a container, including environment
variables:

```sh
sudo docker run --rm -d \
     -p 5000:5432 \
     -v ./database/persistence:/var/lib/postgresql/data \
     -e POSTGRES_DB=db \
     -e POSTGRES_USER=usr \
     -e POSTGRES_PASSWORD=pwd \
     --network app-network \
     --name database-layer \
     me/tp-database
```

> [!IMPORTANT]
> **Question 1-1** : _Document your database container essentials:
> commands and Dockerfile._
> 
> To create an image:
>  - Create a file named `Dockerfile`,
>  - Use the keyword `FROM` to start from a base image: `FROM alpine:3.18`,
>  - You can copy files from the host to the container using the `COPY` keyword.
>  - Build an image from a Dockerfile with `sudo docker build -t <image-name> <path>`.
>
> To create containers:
>  - The base command is `sudo docker run <image-name>`,
>  - Specify a port mapping to expose the container to with the `-p` option,
>  - A container can be named with the option `--name`,
>  - The `-e` option can be used to provide environment variables.

## Create the back-end

We'll now create a Java app in the `backend/` directory.

### Step 1, a dead simple app

Here is a classic "hello world" Java program.

```java
public class Main {
    public static void main(String[] args) {
        System.out.println("Hello World!");
    }
}
```

Here is a Dockerfile that can run it (notice the `java Main` command):

```dockerfile
FROM amazoncorretto:17

COPY Main.class /

CMD ["java", "Main"]
```
Java's _Hello World_.

Here is a Dockerfile that can run it (notice the `java Main` command
to run the class):

```sh
sudo docker build -t me/tp1-backend backend/
```
```sh
sudo docker run -d \
     --name backend-layer \
     me/tp1-backend
```

It works, printing "Hello World!".

### Simple API

Let's create a more complex app.

[Spring initializr](https://start.spring.io/) is used to create
a Spring demo. This scaffold is extracted in `backend/`.

The directory now has (we kept the previous `Main.java` file):

```
$ ls backend/
Dockerfile HELP.md Main.class Main.java mvnw mvnw.cmd pom.xml src
```

We now use this multi-stage Dockerfile to compile and run the program:

```dockerfile
# Build
FROM maven:3.8.6-amazoncorretto-17 AS myapp-build
ENV MYAPP_HOME /opt/myapp
WORKDIR $MYAPP_HOME
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Run
FROM amazoncorretto:17
ENV MYAPP_HOME /opt/myapp
WORKDIR $MYAPP_HOME
COPY --from=myapp-build $MYAPP_HOME/target/*.jar $MYAPP_HOME/myapp.jar
ENTRYPOINT java -jar myapp.jar
```

With ` RUN mvn dependency:go-offline` after the `pom.xml` file is
copied to save the dependencies between builds.

Building the image with the new Dockerfile and instantiating a
container is as was done before:

```sh
sudo docker build -t me/tp1-backend backend/
```
```sh
sudo docker run -d \
     -p 8000:8080 \
     --network app-network \
     --name backend-layer \
     me/tp1-backend
```

When visiting <http://[::]:8080?name=Émile">[^1], the
following response is obtained, as expected:

```json
{
	"id":2,
	"content":"Hello, Émile!"
}
```

> [!IMPORTANT]
> **Question 1-2**:_Why do we need a multistage build? And explain each step of
> this dockerfile._
>
>	A multistage build is a way to optimise Dockerfiles. They allow
>	both a file to be more readable and to reduce the resulting image
>	size.
>
>	To do this, the image is first primed with a first base image, a
>	building step is run, then a new image is started (it can have a
>	different base). The necessary files can be copied from one step
>	to the other. Once all steps are finished, all the images are
>	discarded but the final one.
>
>	This means the setup needed to compile and build an app is not
>	kept in the final image, and only the executable is copied,
>	allowing the remaining image to have a reduced size.
>
>	Because each step is conscripted to its own stage, its own image,
>	the relationship between them can be more clearly laid down.
>
>	The steps:
>	1. Select `maven` as the base image (in order to build with Maven).
>	1. Create an environment variable `$MYAPP_HOME`, set to `/opt/myapp`.
>	1. Go to `/opt/myapp`.
>	1. Copy the `pom.xml` file there.
>	1. Download the dependencies offline (for Docker layer cache).
>	1. Copy full source tree to `./src`.
>	1. Compile the app.
>	1. Create a new image based `amazoncorretto` (Maven is not needed anymore once it is built).
>	1. Re-create the environment variable `$MYAPP_HOME`, set to `/opt/myapp` (its a new image), go to it.
>	1. Copy the Jar produced during the build.
>	1. Set the image's entrypoint to run the obtained Jar.

[^1]: IPv6 is the present, but you can replace `[::]` with `localhost`
if it doesn't work.

### Full API

We'll run use a more complete Java backend that provides a more
complex API. Since we a right now focused on the operations more than
the development, we'll use a ready-made Java application.

We download it from <https://github.com/takima-training/simple-api-student>.

The Dockerfile used previously for the Spring demo was not specific,
and will function for most Maven-based Java project, including this
one. This means we can reuse it..

Once built with the same name, it is run as before:

```sh
sudo docker run -d \
     -p 8000:8080 \
     --network app-network \
     --name backend-layer \
     me/tp1-backend
```

But it fails, on the ground of database access.

Because it is more complex, this program needs to use the database we
created earlier. Reusing the same environment variables as previously
for a clean setup, information in how to talk to the database is added
to `application.yml`:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          lob:
            non_contextual_creation: true
    generate-ddl: false
    open-in-view: true
  datasource:
    url: jdbc:postgresql://database-layer:5432/${POSTGRES_DB}
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver
management:
 server:
   add-application-context-header: false
 endpoints:
   web:
     exposure:
       include: health,info,env,metrics,beans,configprops
```

In `jdbc:postgresql://database-layer:5432/${POSTGRES_DB}`,
`database-layer` is the name of the container running the database. In
a network, Docker automatically fills in the hostnames.

Running a container with the application and supplying the appropriate
variables looks now like:

```sh
sudo docker run --rm -d \
     -p 8000:8080 \
     --network app-network \
     -e POSTGRES_DB=db \
     -e POSTGRES_USER=usr \
     -e POSTGRES_PASSWORD=pwd \
     --name backend-layer \
     me/tp1-backend
```

## HTTP server

No modern web app is complete without at least one reverse proxy. So
let's add one.

First, let's create a new directory for this part, `http/`.

### A basic server to begin with

At its core, a reverse proxy is a classic HTTP server. So let's setup
one that displays an HTML docuemnt to show it works. We'll use
Apache's _httpd_ for this.

A Dockerfile with a basic configuration for this task looks like
:
```dockerfile
FROM httpd:2.4
COPY ./index.html /usr/local/apache2/htdocs/
```
Let's build the image and run a container from it:

```sh
sudo docker build -t me/tp1-http http/
```
```sh
sudo docker run -d \
     -p 8888:80 \
     --name http-layer \
     me/tp1-http
```

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="color-scheme" content="light dark">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <title>A good HTML "Hello world"</title>
    <style>
      body{
        margin: auto;
	    max-width: 80ch;
        font: sans-serif;
      }
    </style>
  </head>
  <body>
    <h1>Hello world!</h1>
  </body>
</html>
```
The `index.html` file used for the demo.

Visiting <http://[::]:8888> displays our HTML file.

#### A few Docker commmands

`docker stats` shows for each running container some information about its resources use, like CPU
fraction used or memory comsumed.

`docker inspect <container>` gives detailed information about th etarget container, by default in a
JSON format.

`docker logs <container>` shows the standard output of the target running container. In the HTTP
server’s case, this include the connexion logs.

### Reverse proxy

A reverse proxy is commonly used to hide or abstract a service’s
architecture, in case there are many servers.

To set one up, we need to configure our HTTP server to behave like
one. Let’s retrieve the server’s default configuration to base ours on
it. We use `docker cp`, which can copy files between containers and
the host.

```sh
sudo docker cp http-layer:/usr/local/apache2/conf/httpd.conf http/httpd.conf
```

The file is quite long, so not shown here.

Here are the changes that were made to the configuration to turn it
into a reverse proxy:

```diff
--- http/httpd.conf
+++ http/httpd.conf
@@ -225,4 +225,14 @@
 #

+<VirtualHost *:80>
+	ProxyPreserveHost On
+	ProxyPass / http://backend-layer:8080/
+	ProxyPassReverse / http://backend-layer:8080/
+</VirtualHost>
+LoadModule proxy_module modules/mod_proxy.so
+LoadModule proxy_http_module modules/mod_proxy_http.so
+
 #
 # ServerAdmin: Your address, where problems with the server should be
@@ -230,4 +240,5 @@
 #
 #ServerName www.example.com:80
+ServerName localhost:80

 #
```

The new configuration file must now be copied in the image to replace
the default one. Since the server will redirect all requests, we don't
need the HTML file anymore.

```dockerfile
FROM httpd:2.4
# COPY ./index.html /usr/local/apache2/htdocs/
COPY ./httpd.conf /usr/local/apache2/conf/httpd.conf
```

After building, running a container (in the network) is done with:

```sh
sudo docker run --rm -d \
     -p 80:80 \
     --network app-network \
     --name http-layer \
     me/tp1-http
```

The proxy runs on the port 80, the default port for HTTP, so we don't
have to specify a port when doing requests.

And now, it behaves transparently, like if we were talking directly to
the back-end.

## Wrapping it all with Compose

Executing the Docker commands works well, but it is a bit
tedious. Fortunately, Compose is here for us.

With Compose, we list all the properties we want out containers to
have, and they are automagically provisioned. Once we don"t need them,
we ask Compose to stop them, and they are removed.

To have the same functionnality as we had spinning the containers up
manually, we write the following `compose.yaml` file:

```yaml
version: '3.7'

services:
    backend:
        build: ./backend
        container_name: backend-layer
        networks:
            - my-network
        depends_on:
            - database
        env_file: .env

    database:
        build: ./database
        container_name: database-layer
        networks:
            - my-network
        volumes:
            - db-persitence:/var/lib/postgresql/data
        env_file: .env

    httpd:
        build: ./http
        container_name: frontend-layer
        ports:
            - "80:80"
        networks:
            - my-network
        depends_on:
            - backend

networks:
    my-network: {}

volumes:
    db-persitence: {}
```

> [!IMPORTANT]
> **Question 1-3**: _Document docker-compose most important commands._
>
>	In symetry: `sudo docker compose up` to create the containers and
>	`sudo docker compose down` to remove them.
>
>	Then, `sudo docker compose logs` is really useful to see what
>	happens in the containers if they are in detached mode.


> [!IMPORTANT]
> **Question 1-4**: _Document your docker-compose file._
>
>	The containers need to live in the same network, so to communicate
>	with each other but be isolated from potential other
>	containers. This is done with the `networks` key. The actual name of
>	the container does not really matter since we only have one and it
>	is name only used in the file.
>
>	Similarly, it is better to declare a volume for the database
>	storage, allowing to restore the state at startup and not have to
>	recreate all entries. We create a named volume in the `volumes`
>	key and bind it in the databse service definition.
>
>	Because we hard-coded the names of the containers in the URLs of
>	the various configurations, we need to keep the same ones
>	here. Hence the `container_name` keys.  Set the container names
>
>	The database and back-end containers need some environment
>	variables to work well. Since some are the same, an `.env` file is
>	created to host the variables, and is provided to the containers
>	with the `env_file` key.
>
>	Because the startup order of the containers can have an impact
>	(the proxy must be able to reach the back-end, which must access
>	the database), we declare a dependency tree with the `depends_on`
>	key.

## Publishing the images

I published the images as `e10e3/ops-database`, `e10e3/ops-backend`,
and `e10e3/ops-frontend`#footnote[This last name will cause problems
later and is changed in a following section.].

Let's use the database as an example of how it's done.

First, building the image, with tag and version:

```sh
sudo docker build -t e10e3/ops-database:1.0 database/
```

Then, publishing it:

```sh
sudo docker push e10e3/ops-database:1.0
```

The result is: https://hub.docker.com/repository/docker/e10e3/ops-database/

> [!IMPORTANT]
> **Question 1-5**: _Document your publication commands and published images
> in dockerhub._
>
>	To push an image to Docker Hub, you must namespace it with you
>	Docker Hub username first. In my case, this is _e10e3_. The tag of
>	an image is set at build time (with the `--tag` option) or with the
>	`docker image rename` command.
>
>	In order to push an image to your account, it is necessary to
>	first log in. This is done with the command `docker login`.
>
>	Once an image is created and the account information entered, the
>	image can be sent online with the `docker push` command.
