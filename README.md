# DevOps methodology

[Docker](https://www.docker.com/). Everything Docker.

## Setup

You will need Docker and Docker Compose to run this project.

First, set the environment variables by filling in the `.env` file:
```shell
cp .env.sample .env
$EDITOR .env
```

Then, build and start the containers with Docker Compose:
```shell
sudo docker compose up
```
