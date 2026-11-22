.PHONY: install setup run test build db-up db-down db-reset psql

install:
	bash scripts/install.sh

setup:
	bash scripts/java-install.sh
	bash scripts/setup.sh

run:
	bash scripts/maven.sh spring-boot:run

test:
	bash scripts/maven.sh clean test

build:
	bash scripts/maven.sh clean package

db-up:
	bash scripts/docker-compose.sh up -d

db-down:
	bash scripts/docker-compose.sh down

db-reset:
	bash scripts/db-reset.sh

psql:
	bash scripts/docker-compose.sh exec postgres sh -c 'exec psql -U "$$POSTGRES_USER" -d "$$POSTGRES_DB"'