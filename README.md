# FocaAI API

API em Java (Spring Boot) + PostgreSQL do [FocaAI](https://github.com/brunotaveiradasilva/focaai).

## Stack

- Java 21
- Spring Boot 4 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL 18, com o schema versionado pelo Flyway (`src/main/resources/db/migration`)
- Docker / docker compose para rodar local

## Rodando local

### Opção 1 — Docker (não precisa instalar Java nem PostgreSQL)

```bash
docker compose up --build
```

Sobe o PostgreSQL e a API juntos. A API fica em `http://localhost:8080`
(`GET /actuator/health` responde `{"status":"UP"}`).

### Opção 2 — Java na máquina

Precisa de JDK 21. O Maven não precisa estar instalado: o `mvnw` baixa sozinho.
Suba só o banco e rode a API:

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

As variáveis de ambiente aceitas estão em `.env.example`.

## Testes

```bash
./mvnw verify
```

Os testes usam um PostgreSQL embutido (mesma versão do `docker-compose.yml`), que roda dentro
do processo do teste: não precisa de Docker nem de banco instalado.
