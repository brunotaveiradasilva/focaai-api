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

## Login

O app entra com a conta Google; a API não tem senha própria. Fora o conteúdo, o login e o
`/actuator/health`, tudo exige `Authorization: Bearer <accessToken>`.

1. O app faz o login com o Google e recebe um **ID token**.
2. Manda para `POST /api/auth/google` com `{"idToken": "..."}`. A API confere a assinatura (chaves
   públicas do Google), a validade, o emissor, se o e-mail é verificado e se o token foi emitido
   para um dos client IDs do FocaAI (`GOOGLE_CLIENT_IDS`). O primeiro login cria a conta.
3. A resposta traz `accessToken` (JWT da API, vale `JWT_ACESSO_MINUTOS`), `refreshToken`
   (vale `JWT_RENOVACAO_DIAS`) e `user` (nome, e-mail e foto do Google).
4. Quando o `accessToken` vence, `POST /api/auth/renovar` com `{"refreshToken": "..."}` devolve
   um par novo. Cada refresh token serve uma vez só; o banco guarda apenas o hash dele.
5. `POST /api/auth/sair` com `{"refreshToken": "..."}` encerra a sessão daquele aparelho.

`GET /api/eu` devolve a conta logada.

### Client IDs do Google

No [Google Cloud Console](https://console.cloud.google.com/apis/credentials), no projeto do
FocaAI, crie credenciais **ID do cliente OAuth** do tipo:

- **Aplicativo da Web**: usado pelo app web e pelo login no Android.
- **Android**: pacote `com.focaai.app` + impressão digital SHA-1 do certificado de assinatura
  (`bunx eas-cli credentials` mostra a do build).
- **iOS**: bundle ID `com.focaai.app`.

Coloque os client IDs em `GOOGLE_CLIENT_IDS`, separados por vírgula. Sem eles a API sobe, mas
recusa todo login.

## Dados do aluno

Tudo em `/api/eu/...` é do aluno logado. Os campos JSON têm os mesmos nomes do store do app.

| Endpoint | O que faz |
|---|---|
| `GET /api/eu/estado` | Perfil, progresso por assunto, foco e reforço por matéria e os últimos ajustes. `profile: null` = ainda não fez o onboarding |
| `PUT /api/eu/perfil` | Fim do onboarding. No primeiro, as matérias que o aluno domina começam com o primeiro grupo feito; refazer só troca o perfil |
| `PATCH /api/eu/perfil` | Muda só os campos enviados (ex.: `{"minutesPerDay": 120}`) |
| `PUT /api/eu/progresso/{assunto}` | `{"done": true}` marca o assunto como feito; `false` volta para zero |
| `PUT` / `DELETE /api/eu/foco/{materia}` | Escolhe (`{"topicId": "..."}`) ou tira o assunto de foco da matéria |
| `POST /api/eu/checkins` | Registra o estudo. A API avalia (aulas creditadas, reforço da matéria e o ajuste explicado ao aluno) e devolve o estado novo. Mandar um `id` próprio evita duplicar se o app reenviar |
| `GET /api/eu/checkins` | Histórico de check-ins |
| `DELETE /api/eu/dados` | "Começar do zero": apaga o estudo e mantém a conta |
| `DELETE /api/eu` | Exclui a conta e tudo dela |
