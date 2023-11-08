# DevOps methodology

[Docker](https://www.docker.com/). Everything Docker.

## Where can I find the report?

You can find the report [here](./report/).

## Setup locally with Docker Compose

You will obviously need Docker and Docker Compose to run the project with
this method.

First, set the environment variables by filling in the `.env` file:
```shell
cp .env.sample .env
$EDITOR .env
```

Then, build and start the containers with Docker Compose:
```shell
sudo docker compose up
```

Add the `--detach` option if you want the containers to run in the background.

## Setup on a remote server with Ansible

You need a standard distribution of Ansible to use this method, and a server.

We'll suppose you have already added your server's hostname to Ansible's
[hosts file](https://docs.ansible.com/ansible/latest/getting_started/get_started_ansible.html).

First, enter the `ansible` directory:
```shell
cd ansible
```

Then, set the play variables by filling in the `env.yaml` file:
```shell
cp env.template.yaml env.yaml
$EDITOR env.yaml
```

You'll also need to indicate the path to the SSH private key you wish to use:
```shell
cp inventories/group_vars/all.template.yaml inventories/group_vars/all.yaml
$EDITOR inventories/group_vars/all.yaml
```

Once this is done, execute the playbook:
```shell
ansible-playbook -i inventories/setup.yaml playbook.yaml
```

Once the command stops, the project should be running on your server.
