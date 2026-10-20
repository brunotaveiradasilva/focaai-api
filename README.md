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

### Opção 3 — sem Docker e sem PostgreSQL

Só com o JDK 21:

```bash
./mvnw spring-boot:test-run
```

Sobe a API com o PostgreSQL embutido dos testes (os dados somem quando ela para), mostra no log os
e-mails que seriam enviados e liga um login de desenvolvimento, para usar o app antes de configurar o Google:

```bash
curl -X POST http://localhost:8080/api/auth/dev -H "Content-Type: application/json" \
  -d '{"email":"aluno@example.com","name":"Aluno"}'
```

Esse login e o banco embutido ficam nos fontes de teste (`FocaaiApiLocal`): não existem no jar publicado.

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
| `GET /api/conteudo/editais` e `/editais/{id}` | Mapeamento edital → árvore de conteúdos (ENEM, UFMS 2027 e Fuvest 2027) |
| `GET /api/conteudo/editais/{id}/topicos?materia=fil` | Assuntos de um edital numa matéria, do mais pesado ao mais leve |
| `GET /api/conteudo/provas/{id}` | Calendário e estrutura da prova (`ufms-2027`, com os pesos por curso, e `fuvest-2027`) |

Para mudar uma data de prova ou um edital, edite o JSON e publique uma nova versão da API. Um
vestibular novo com edital mapeado ganha `edital.<id>.json` e `prova.<id>.json`, entra em
`Catalogo.VESTIBULARES_MAPEADOS` e em `editalDaProva` no `catalogo.json`. Unicamp e Unesp ainda
não têm edital mapeado: todos os assuntos contam como "citado em parte" (peso 0,5).

O mapeamento da Fuvest saiu do Guia de Provas e Programas da edição 2026 (o de 2027 não estava
publicado em PDF) e ainda não foi revisado por professor; o trecho de Sociologia do guia foi lido
só até a habilidade 14.

### Provas anteriores

`ferramentas/provas-anteriores/` tem o índice do acervo oficial da Fuvest (2000 a 2026), o
catálogo de onde estão as provas da UFMS e dois scripts em Python: `baixar_acervo_fuvest.py` lista
(e com `--baixar`, baixa) os PDFs do acervo respeitando o `robots.txt`, e `extrair_questoes.py`
transforma um PDF de prova em rascunhos de questão para revisão humana. Nada disso entra no app:
toda questão sai como `rascunho`, com direitos `pendente`, até a Fuvest/USP e a UFMS autorizarem
o uso. A 1ª fase da Fuvest tinha 90 questões até 2026 e tem 80 a partir de 2027: não misture os
dois modelos em estatísticas.
`docs/modelo-de-dados-rascunho.sql` é o rascunho do modelo para quando esse conteúdo e o banco
de questões forem para o PostgreSQL.

## Login

O app entra com a conta Google ou com e-mail e senha. Fora o conteúdo, o login e o
`/actuator/health`, tudo exige `Authorization: Bearer <accessToken>`.

Com Google:

1. O app faz o login com o Google e recebe um **ID token**.
2. Manda para `POST /api/auth/google` com `{"idToken": "..."}`. A API confere a assinatura (chaves
   públicas do Google), a validade, o emissor, se o e-mail é verificado e se o token foi emitido
   para um dos client IDs do FocaAI (`GOOGLE_CLIENT_IDS`). O primeiro login cria a conta.
3. A resposta traz `accessToken` (JWT da API, vale `JWT_ACESSO_MINUTOS`), `refreshToken`
   (vale `JWT_RENOVACAO_DIAS`) e `user` (nome, e-mail e foto do Google).
4. Quando o `accessToken` vence, `POST /api/auth/renovar` com `{"refreshToken": "..."}` devolve
   um par novo. Cada refresh token serve uma vez só; o banco guarda apenas o hash dele.
5. `POST /api/auth/sair` com `{"refreshToken": "..."}` encerra a sessão daquele aparelho.

Com e-mail e senha (a conta só existe depois de o e-mail ser confirmado por um código de 6 dígitos):

| Endpoint | Corpo | O que faz |
|---|---|---|
| `POST /api/auth/cadastro` | `email`, `name`, `password` | Manda o código para o e-mail (204). 409 se o e-mail já tem senha; 429 se pediu outro há menos de 1 minuto |
| `POST /api/auth/cadastro/confirmar` | `email`, `code` | Cria a conta e já entra (mesma resposta do Google) |
| `POST /api/auth/entrar` | `email`, `password` | Entra. 10 senhas erradas seguidas bloqueiam a conta por 15 minutos (429) |
| `POST /api/auth/senha/esqueci` | `email` | Manda o código para criar uma senha nova. Sempre 204, tenha conta ou não |
| `POST /api/auth/senha/redefinir` | `email`, `code`, `password` | Troca a senha, derruba os outros aparelhos e entra |

O código vale 15 minutos e aguenta 5 erros. Senha com 8 a 72 caracteres, guardada em bcrypt. Como
todo e-mail é confirmado, Google e senha no mesmo e-mail são a mesma conta: dá para entrar dos
dois jeitos, e uma conta Google ganha senha pelo cadastro ou pelo "esqueci minha senha".

`GET /api/eu` devolve a conta logada.

### E-mails (Resend)

Os códigos saem pela API HTTP do [Resend](https://resend.com) (o Railway bloqueia SMTP). Crie a
conta, gere uma **API key** (permissão *Sending access*) e coloque em `RESEND_API_KEY`. Sem ela, o
login com e-mail responde 503 e só o Google funciona.

O remetente padrão, `onboarding@resend.dev`, **só entrega para o e-mail da própria conta do
Resend**: serve para testar. Para mandar para qualquer pessoa, verifique um domínio em
**Domains** no Resend e troque `EMAIL_REMETENTE` (ex.: `FocaAI <nao-responda@seudominio.com.br>`).

Rodando local (opção 3), os e-mails não saem: o código aparece no log da API.

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

## Rede (amizades, posts e comentários)

Entre alunos de verdade. Amizade é mútua: um pede, o outro aceita. Um post só aparece para o autor e
os amigos dele, e só eles comentam; para os outros, o post responde 404. Ninguém vê o e-mail de
ninguém: só nome, foto e bio.

| Endpoint | O que faz |
|---|---|
| `GET /api/usuarios?busca=texto` | Alunos com o texto no nome (mín. 2 letras, até 20), com a relação com cada um |
| `GET /api/usuarios/{id}` | Perfil: nome, foto, bio, nº de amigos e de posts, e a relação (`self`, `friends`, `sent`, `received` ou nula) |
| `GET /api/usuarios/{id}/posts?antes=` | Posts do aluno (403 se não for amigo) |
| `PUT /api/eu/perfil-publico` | Muda nome (até 50), bio (até 160; vazia apaga) e foto (`{ name, bio, picture }`). Depois disso o login com Google não sobrescreve mais nome e foto |
| `POST /api/imagens` | Envia uma foto (`{ "data": base64 }`, JPEG, PNG ou WebP, até 2 MB) e devolve `{ id, url }` |
| `GET /api/imagens/{id}` | A foto, sem token (o id aleatório é o endereço), com cache de um ano |
| `GET /api/eu/amigos` | Amigos, pedidos recebidos e pedidos enviados |
| `PUT /api/eu/amigos/{id}` | Pede amizade, ou aceita se a outra pessoa já tinha pedido |
| `DELETE /api/eu/amigos/{id}` | Desfaz a amizade, cancela o pedido enviado ou recusa o recebido |
| `GET /api/eu/feed?antes=` | Posts do aluno e dos amigos, do mais novo ao mais antigo, 20 por página (`next` vai em `antes`) |
| `POST /api/posts` | Publica (`{ "text", "image" }`: texto até 2000 caracteres, foto opcional vinda de `/api/imagens`; com foto, o texto pode ficar vazio) |
| `GET` / `DELETE /api/posts/{id}` | Abre o post com os comentários / apaga (só o autor) |
| `POST /api/posts/{id}/comentarios` | Comenta (até 500 caracteres) |
| `DELETE /api/posts/{id}/comentarios/{id}` | Apaga o comentário (quem escreveu ou o autor do post) |

## Importar os dados do aparelho

Antes da API, o app guardava tudo só no aparelho. No primeiro login, ele manda esse estado
(o `focaai-state-v1` do AsyncStorage) para `POST /api/eu/importar`, e a API grava perfil,
progresso, check-ins, simulados, planos congelados, tarefas dispensadas, conexões e grupos de uma
vez. Os ids do app (timestamps) viram UUIDs estáveis, então reenviar não duplica. Uma conta que já
tem perfil recusa a importação (409) em vez de misturar dois históricos.

## Deploy (Railway)

1. Crie uma conta em [railway.app](https://railway.app) (dá para logar com a conta do GitHub).
2. **New Project → Deploy from GitHub repo** e escolha `focaai-api`. O Railway detecta o
   `Dockerfile` sozinho e builda a partir dele.
3. **New → Database → Add PostgreSQL** no mesmo projeto. O Railway cria as variáveis `PGHOST`,
   `PGPORT`, `PGDATABASE`, `PGUSER` e `PGPASSWORD` automaticamente.
4. No serviço da API, aba **Variables**, defina:
   - `DB_URL` = `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}`
   - `DB_USERNAME` = `${{Postgres.PGUSER}}`
   - `DB_PASSWORD` = `${{Postgres.PGPASSWORD}}`
   - `JWT_SECRET` = um valor aleatório forte (gere com `openssl rand -base64 48`, por exemplo)
   - `GOOGLE_CLIENT_IDS` = os client IDs OAuth do app (ver **Client IDs do Google**)
   - `CORS_ALLOWED_ORIGINS` = o endereço da versão web do app, se ela for publicada
   - `RESEND_API_KEY` e `EMAIL_REMETENTE` = envio dos códigos do login com e-mail (ver **E-mails (Resend)**)
   - (o Railway já define `PORT` sozinho — não precisa mexer)
5. Em **Settings → Deploy**, coloque `/actuator/health` como **Healthcheck Path** e aplique com o
   botão **Deploy** da faixa de mudanças no topo: o Railway só troca a versão no ar depois que a nova
   responder. (Fica no painel e não num `railway.json`: esse formato deixa de funcionar em 01/12/2026.)
6. Gere um domínio público em **Settings → Networking → Generate Domain**. Essa URL
   (`https://algo.up.railway.app`) é o `EXPO_PUBLIC_API_URL` do app. Nos builds do EAS, cadastre
   com `bunx eas-cli env:create` (ou pela página do projeto no expo.dev) para valer em todos os perfis.

O schema do banco é criado e atualizado pelo Flyway a cada deploy. Na primeira subida, a API
baixa em segundo plano as provas do ENEM que faltam (alguns minutos; o primeiro simulado de cada
prova pode demorar um pouco enquanto isso).
