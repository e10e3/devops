# Ansible

Let's now deploy our app to a real server. I was assigned one,
reachable with the domain name #link("emile.royer.takima.cloud"). Its
only account is called _centos_ (after the distribution installed on
it, CentOS[^1]).

Since this is a distant server, the preferred method to access it is
with SSH. A key pair was generated, with the public one installed
already on the server. The private one, `id_rsa`, is stored on our
side, in the parent directory to our project.

Let's connect to our server:
```sh
ssh -i ../id_rsa centos@emile.royer.takima.cloud
```
Great, it works.

```
[centos@ip-10-0-1-232 ~]$ id
uid=1000(centos) gid=1000(centos) groups=1000(centos),4(adm),190(systemd-journal) context=unconfined_u:unconfined_r:unconfined_t:s0-s0:c0.c1023
```

To deploy our apps on the server, we could use Docker Compose, as they
are already packaged. But this means logging in the server with SSH,
transferring files between hosts and running the commands manually.

We'll use _Ansible_ to automate the deployment and discover how it
could be done if we were operating at a larger scale. We only have one
server, so it's a bit overkill, but will this stop us?

Ansible is configured using a tree of files, making it play nicely
with POSIX systems. It happens to only run on them anyway.

[^1]: Buried by RedHat, RIP CentOS.

## Configuring the server with Ansible

In order to use Ansible with our server, we need to register
it. Ansible uses its own file `/etc/ansible/hosts`. To add our server,
we write its domain name in this file:

```sh
sudo echo "emile.royer.takima.cloud" > /etc/ansible/hosts
```

It can then be pinged with Ansible:
```sh
$ ansible all -m ping --private-key=../id_rsa -u centos
emile.royer.takima.cloud | SUCCESS => {
    "ansible_facts": {
        "discovered_interpreter_python": "/usr/bin/python"
    },
    "changed": false,
    "ping": "pong"
}
```

Let's install an apache httpd server to how it works well:
```sh
ansible all -m yum -a "name=httpd state=present" --private-key=../id_rsa -u centos --become
```

Now create and HTML file to display and start the service:
```sh
ansible all -m shell -a 'echo "<html><h1>Hello World</h1></html>" >> /var/www/html/index.html' --private-key=../id_rsa -u centos --become
ansible all -m service -a "name=httpd state=started" --private-key=../id_rsa -u centos --become
```

Visiting http://emile.royer.takima.cloud diplays "Hello World" as expected.

An inventory is created in order not to have to give the path to the
private key at each command:

```yaml
all:
 vars:
   ansible_user: centos
   ansible_ssh_private_key_file: ~/schol/devops/id_rsa
 children:
   prod:
     hosts: emile.royer.takima.cloud
```
The inventory file at `ansible/inventories/setup.yaml`

Using the inventory works just as well:

```sh
$ ansible all -i ansible/inventories/setup.yaml -m ping
emile.royer.takima.cloud | SUCCESS => {
    "ansible_facts": {
        "discovered_interpreter_python": "/usr/bin/python"
    },
    "changed": false,
    "ping": "pong"
}
```

It is also possible to display information about the target systems:

```sh
$ ansible all -i ansible/inventories/setup.yaml -m setup -a "filter=ansible_distribution*"
emile.royer.takima.cloud | SUCCESS => {
    "ansible_facts": {
        "ansible_distribution": "CentOS",
        "ansible_distribution_file_parsed": true,
        "ansible_distribution_file_path": "/etc/redhat-release",
        "ansible_distribution_file_variety": "RedHat",
        "ansible_distribution_major_version": "7",
        "ansible_distribution_release": "Core",
        "ansible_distribution_version": "7.9",
        "discovered_interpreter_python": "/usr/bin/python"
    },
    "changed": false
}
```

> [!IMPORTANT]
> **Question 3-1**: _Document your inventory and base commands_
>
>	An Ansible inventory is grouped in categories. Here, only one
>	group of hosts is created: the default _all_.
>
>	For each group, variables can be created. Here, it is the user and
>	the private key file path.
>
>	Groups can have children (subgroups): _prod_ is implicitly created
>	as a subgroup of _all_. It has one host: our server.
>
>	For now, we only used the base `ansible(1)` command. Its one
>	required argument is <pattern>, indicating which groups to
>	target. Some useful options are:
>
>	`-m`: The Ansible module to run.
>
>	`-a`: The arguments to give to the module.
>
>	`-i`: The inventory to use.
>
>	`--private-key`: Which private key should be used.

## Creating a playbook

Just like with Docker, it is tedious to run so many commands,
especially when there are many packages to set up on the hosts.

But just like with Docker, there is a solution: playbooks.

Let's create a basic one:

`ansible/playbook.yaml`
```yaml
- hosts: all
  gather_facts: false
  become: true

  tasks:
   - name: Test connection
     ping:
```

Execute the playbook on our server:
```
ansible-playbook-i ansible/inventories/setup.yaml ansible/playbook.yaml
```

The ping is successful.

Because our apps are containerised, it'll be needed to install Docker
on the server. A playbook to do this looks like:

`docker-playbook.yaml`
```yaml
---
- hosts: all
  gather_facts: false
  become: true

# Install Docker
  tasks:

  - name: Install device-mapper-persistent-data
    yum:
      name: device-mapper-persistent-data
      state: latest

  - name: Install lvm2
    yum:
      name: lvm2
      state: latest

  - name: add repo docker
    command:
      cmd: sudo yum-config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo

  - name: Install Docker
    yum:
      name: docker-ce
      state: present

  - name: Make sure Docker is running
    service: name=docker state=started
    tags: docker
```

Ansible has the notion of _roles_, where reusable tasks can be
defined. A role can be added to any number of playbooks, and its tasks
are then added to them, without having to all the tasks by hand.

`ansible-galaxy` helps creating roles:

```sh
ansible-galaxy init roles/docker
```

The _docker_ role is outfitted with the tasks that were in the
playbook to install Docker.

`roles/docker/tasks/main.yml`
```yaml
- name: Install device-mapper-persistent-data
  yum:
    name: device-mapper-persistent-data
    state: latest

- name: Install lvm2
  yum:
    name: lvm2
    state: latest

- name: add repo docker
  command:
    cmd: sudo yum-config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo

- name: Install Docker
  yum:
    name: docker-ce
    state: present

- name: Make sure Docker is running
  service: name=docker state=started
  tags: docker
```

And the _docker_ role is added to the cleaned-up playbook:

```yaml
---
- hosts: all
  gather_facts: false
  become: true

  tasks:
   - name: Test connection
     ping:

  roles:
    - docker
```

> [!IMPORTANT]
> **Question 3-2**: _Document your playbook_
>
>	The playbook is now really simple --- only a bit more complex that
>	the basic one at the start.
>
>	It declares to apply to all the hosts, and to execute the commands
>	as root (it says "become root").
>
>	The tasks from its roles are added to the list, meaning all the
>	tasks there were before to install Docker. Finally, as the cherry
>	on the cake, a _ping_ is added, just to be sure.

## Deploying our app

Let's now create a number of roles, one for each step need to have our
API running online.

The roles we created are `create_network`, `create_volume`,
`install_docker`, `launch_app`, `launch_database`, `launch_proxy`, and
`setup-env-file`.

**Install Docker**: This is the one called _docker_ previously. It ensures Docker is installed in the environment.

**Create network**: This role creates a named network for all our
 container to be in, so they are isolated and can communicate with
 each other.

**Create volume**: This role creates a named volume in order to persist
 our database's entries, even between restarts.

**Launch database**: This one starts the database, with its environment
 variables, the volume and the network.

**Launch app**: This role starts the back-end with the environment
 variables necessary to connect to the database and is attached to the
 network.

**Launch proxy**: This role starts the HTTP proxy server, includes it in
 the network and maps its port so that the hosts' port 80 redirects to
 it.

**Setup env file**: This transient role is a dependency of the database
 and app roles. It is not called directly in our playbook. Its role is
 to copy our `.env` file to the server (in `/root/ansible-env`), in
 order for the other riles to access its content.

The task each contains is an equivalent of what the `compose.yaml`
file what doing for Docker Compose.

Here is the `task/main.yml` file for _launch\_database_:
```yaml
---
- name: Create the database container
  community.docker.docker_container:
    name: database-layer
    image: e10e3/ops-database
    pull: true
    volumes:
      - db_storage:/var/lib/postgresql/data
    networks:
      - name: app_network
    env-files:
      - /root/ansible-env
```

All of the roles are called in the playbook.

`playbook.yaml`
```yaml
---
- hosts: all
  gather_facts: false
  become: true

  tasks:
    - name: Test connection
      ping:

  roles:
    - install_docker
    - create_network
    - create_volume
    - launch_database
    - launch_app
    - launch_proxy
```

In order to efficiently use Docker container and have a syntax that
resembles Compose, a few Ansible modules are
used. `community.docker.docker_container` allows to start containers
from images and parameter them, `community.docker.docker_network` can
create networks and `community.docker.docker_volume` will create
storage volumes.

> [!IMPORTANT]
> **Question 3-3**: _Document your `docker_container` tasks configuration._
>
>	A handful of options of `docker_container` are used to configure
>	the containers:
>
>	**name**: Gives a name to the container. Useful to make a reference
>	 to it in an program's configuration.
>
>	**image**: The image to use for the container. It is pulled from
>	 Docker Hub by default.
>
>	**pull**: Whether to systematically fetch the image, even if it
>	 appears not to have changed. Because the images used are all with
>	 the _latest_ tag, this option is useful to force Ansible to use
>	 the latest version of the _latest_ tag.
>
>	**networks**: A list of networks to connect the container to.
>
>	**env**: A mapping of environment variables to create in the
>	 container. Useful for configuration.
>
>	**volumes**: A list for volumes to map the container's directories
>	 to. Only the database has a use for this option in this case.
>
>	**ports**: Port binding for the container. In this case, the proxy's
>	 port 80 is mapped to the server's port 80.

## Ansible Vault

Environment variables to are listed in the roles' tasks, but some
variable's value should stay a secret, whereas roles should be
published. A secure way to handle secrets is needed.

Ansible itself supports variables, that are distinct from environment
variables. These variables can be declared in the playbook, tasks
files, and also in external files.

They allow to parameterise the configuration: a value is no longer
hard-written, but references a variable declared in YAML.

The project's Ansible configuration is put in `ansible/env.yaml`.

```yaml
---
database:
  container_name: "database-layer"
  postgres_name: "db"
  postgres_username: "usr"
  postgres_password: "pwd"
backend:
  container_name: "backend-layer"
```

Ansible has another mechanism to protect secret: the vault.

The Ansible Vault encrypts file and variables in place, and decrypts
them when e.g. a playbook is used. The files can only be decrypted if
the correct pass is given (the same one that was used to encrypt
it). Under the hood, it uses AES-256 to secure the data.

In this situation, the `en.yaml` file was encrypted with
`ansible-vault encrypt env.yaml`, and a vault pass.

Encrypted files can still be referenced in playbooks:

```yaml
---
- hosts: all
  gather_facts: false
  become: true
  vars_files:
    - env.yaml

  tasks:
    - name: Test connection
      ping:

  roles:
    - install_docker
    - create_network
    - create_volume
    - launch_database
    - launch_app
    - launch_proxy
```

... and the decrypted value can be transferred to role (here with the
database):

```yaml
---
- name: Create the database container
  community.docker.docker_container:
    name: "{{ database.container_name }}"
    image: e10e3/ops-database
    volumes:
      - db_storage:/var/lib/postgresql/data
    networks:
      - name: app_network
    env:
      POSTGRES_DB: "{{ database.postgres_name }}"
      POSTGRES_USER: "{{ database.postgres_username }}"
      POSTGRES_PASSWORD: "{{ database.postgres_password }}"
```

A playbook that uses an encrypted file can be launched with:
```
ansible-playbook -i inventories/setup.yaml playbook.yaml --ask-vault-pass
```
to ask for the secret pass.

