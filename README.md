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

## Simulados

Questões reais do ENEM de 2009 a 2023, da [api.enem.dev](https://api.enem.dev) (domínio público).
Cada prova é baixada uma vez, guardada na tabela `prova_enem` e servida a todos os alunos daí em
diante. Ao subir, a API baixa em segundo plano as provas que ainda faltam (`ENEM_AQUECER_CACHE`).

| Endpoint | O que faz |
|---|---|
| `POST /api/eu/simulados` | `{"areas": ["matematica"], "count": 20, "range": "recentes"}` monta um simulado no idioma estrangeiro do perfil, sem repetir questão já vista enquanto houver inédita, e deixa em andamento |
| `GET /api/eu/simulados/em-andamento` | O simulado em andamento (sem gabarito), ou 204 |
| `PUT /api/eu/simulados/em-andamento/respostas/{questao}` | `{"letter": "C"}` marca a resposta; `null` apaga |
| `PUT /api/eu/simulados/em-andamento/tempo` | `{"seconds": 600}` guarda o tempo de prova |
| `POST /api/eu/simulados/em-andamento/finalizar` | Fecha o simulado e devolve gabarito e placar (total e por área) |
| `DELETE /api/eu/simulados/em-andamento` | Descarta o simulado em andamento |
| `GET /api/eu/simulados` e `/{id}` | Simulados finalizados (resumo com placar) e um simulado completo |

## Plano de estudos

O plano é montado por regras, sempre igual para as mesmas entradas: rotina, concorrência do curso,
nível em cada matéria, progresso, foco e reforço (porte exato do `planner.ts` do app, conferido em
`PlanejadorTests` contra 300 cenários gerados pelo próprio app). O plano de cada dia fica congelado
quando o aluno abre o app naquele dia, para as tarefas atrasadas serem conferidas contra o que foi
planejado.

As datas seguem o calendário do aluno: o app manda o fuso em `X-Timezone`
(ex.: `America/Sao_Paulo`; sem ele, vale esse).

| Endpoint | O que faz |
|---|---|
| `GET /api/eu/semana?data=2026-10-09` | A semana (tela Início): tarefas, ids das feitas, atrasadas da última semana e resumo |
| `POST /api/eu/tarefas/{id}/dispensar` | "Deixar pra lá" numa tarefa atrasada |
| `GET /api/eu/plano` | Objetivo, minutos por semana, dias de estudo, provas seguidas e próximas datas |
| `GET /api/eu/roteiros/{materia}` | Roadmap da matéria com estado de cada assunto, aulas feitas, foco e o assunto atual |
| `POST /api/plano/previa` | `{"profile": {...}}` devolve o plano que um perfil ainda não salvo teria (prévia do onboarding) |

## Evolução e Pessoas

| Endpoint | O que faz |
|---|---|
| `GET /api/eu/estatisticas?dias=7` | Sequência atual e melhor, minutos de hoje e por dia, minutos por matéria, acerto geral e por área nos simulados (fuso em `X-Timezone`) |
| `GET /api/eu/pessoas` | Parceiros sugeridos (com motivos, em que podem ajudar e o status do pedido) e os grupos do aluno |
| `POST /api/eu/pessoas/{id}/conectar` | Pede conexão (fica `pending`) |
| `PUT` / `DELETE /api/eu/grupos/{id}` | Entra ou sai de um grupo |

As pessoas sugeridas ainda são exemplos fictícios (`conteudo/pessoas-exemplo.json`), como no app;
a combinação já é a de verdade e vai rodar igual quando houver perfis reais para cruzar.

## Importar os dados do aparelho

Antes da API, o app guardava tudo só no aparelho. No primeiro login, ele manda esse estado
(o `focaai-state-v1` do AsyncStorage) para `POST /api/eu/importar`, e a API grava perfil,
progresso, check-ins, simulados, planos congelados, tarefas dispensadas, conexões e grupos de uma
vez. Os ids do app (timestamps) viram UUIDs estáveis, então reenviar não duplica. Uma conta que já
tem perfil recusa a importação (409) em vez de misturar dois históricos.
