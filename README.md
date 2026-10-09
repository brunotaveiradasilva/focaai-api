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

## Conteúdo

O conteúdo fixo do app (matérias, cursos, calendário de provas, árvore de conteúdos e o mapeamento
de cada edital) fica em `src/main/resources/conteudo/*.json`, que são a fonte da verdade, e é
servido sem login:

| Endpoint | O que traz |
|---|---|
| `GET /api/conteudo` | Matérias, áreas, cursos, concorrência, provas, vestibulares, ciclos e todos os assuntos |
| `GET /api/conteudo/roteiros/{materia}?provas=enem,ufms` | Roadmap da matéria para as provas, com o peso de cada assunto em cada prova |
| `GET /api/conteudo/editais` e `/editais/{id}` | Mapeamento edital → árvore de conteúdos (ENEM e UFMS 2027) |
| `GET /api/conteudo/editais/{id}/topicos?materia=fil` | Assuntos de um edital numa matéria, do mais pesado ao mais leve |
| `GET /api/conteudo/provas/ufms-2027` | Calendário, estrutura da prova e pesos por curso da UFMS 2027 |

Para mudar uma data de prova ou um edital, edite o JSON e publique uma nova versão da API.
`docs/modelo-de-dados-rascunho.sql` é o rascunho do modelo para quando esse conteúdo e o banco
de questões forem para o PostgreSQL.
