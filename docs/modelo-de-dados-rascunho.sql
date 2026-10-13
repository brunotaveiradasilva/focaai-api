-- FocaAI: modelo de dados (PostgreSQL). Versão 0.1 para discussão.

-- 1) Instituições, provas e datas -------------------------------------------
CREATE TABLE institution (id serial PRIMARY KEY, name text NOT NULL, kind text, state char(2), site text);
CREATE TABLE exam (
  id serial PRIMARY KEY, institution_id int REFERENCES institution, name text NOT NULL,
  kind text CHECK (kind IN ('enem','vestibular','seriado','concurso')), year int NOT NULL, edition text);
CREATE TABLE exam_date (            -- uma linha por fase/dia; nunca guardar data sem fonte
  id serial PRIMARY KEY, exam_id int REFERENCES exam, label text NOT NULL,  -- ex.: '1ª fase', '2º dia'
  date date NOT NULL, source_url text NOT NULL, verified_at timestamptz NOT NULL,
  status text CHECK (status IN ('confirmada','alterada','cancelada')) DEFAULT 'confirmada');

-- 2) Árvore mestra de conteúdos ----------------------------------------------
CREATE TABLE topic (
  id text PRIMARY KEY,               -- ex.: 'mat.funcoes.funcao-afim'
  parent_id text REFERENCES topic, subject text NOT NULL, name text NOT NULL, level int NOT NULL, sort int DEFAULT 0);
CREATE TABLE edital_topic (         -- mapeamento de cada edital para a árvore
  exam_id int REFERENCES exam, topic_id text REFERENCES topic,
  weight numeric(3,2) DEFAULT 1.0,  -- 0 a 1; peso estimado da incidência
  excerpt text, reviewed_by text, PRIMARY KEY (exam_id, topic_id));

-- 3) Cursos e concorrência (SISU e notas de corte públicas) ------------------
CREATE TABLE course (id serial PRIMARY KEY, name text NOT NULL, area text, tier int CHECK (tier BETWEEN 1 AND 5));
CREATE TABLE course_offer (
  id serial PRIMARY KEY, course_id int REFERENCES course, institution_id int REFERENCES institution,
  year int, cutoff_score numeric(6,2), source_url text);

-- 4) Banco de questões --------------------------------------------------------
CREATE TABLE source_document (      -- cada PDF baixado, para rastrear origem e direitos
  id serial PRIMARY KEY, exam_id int REFERENCES exam, url text NOT NULL, sha256 text UNIQUE,
  fetched_at timestamptz, rights text CHECK (rights IN ('publico','autorizado','pendente','proibido')) DEFAULT 'pendente');
CREATE TABLE question (
  id bigserial PRIMARY KEY, exam_id int REFERENCES exam, source_id int REFERENCES source_document,
  number int, subject text NOT NULL, statement text NOT NULL, images text[] DEFAULT '{}',
  answer char(1), difficulty int CHECK (difficulty BETWEEN 1 AND 5),
  status text CHECK (status IN ('rascunho','em_revisao','publicada','retirada')) DEFAULT 'rascunho',
  reviewed_by text, created_at timestamptz DEFAULT now());
CREATE TABLE question_option (question_id bigint REFERENCES question, letter char(1), body text, image text, PRIMARY KEY (question_id, letter));
CREATE TABLE question_topic (question_id bigint REFERENCES question, topic_id text REFERENCES topic, confidence numeric(3,2), reviewed boolean DEFAULT false, PRIMARY KEY (question_id, topic_id));
CREATE TABLE explanation (question_id bigint REFERENCES question, body text, author text, status text DEFAULT 'rascunho');

-- 5) Uso do aluno (alimenta Evolução e o replanejamento) ----------------------
CREATE TABLE mock_exam (id bigserial PRIMARY KEY, user_id uuid NOT NULL, size int, subjects text[], created_at timestamptz DEFAULT now(), finished_at timestamptz);
CREATE TABLE attempt (
  id bigserial PRIMARY KEY, user_id uuid NOT NULL, mock_exam_id bigint REFERENCES mock_exam,
  question_id bigint REFERENCES question, chosen char(1), is_correct boolean, time_ms int, at timestamptz DEFAULT now());
CREATE TABLE topic_progress (user_id uuid, topic_id text REFERENCES topic, status text CHECK (status IN ('nao_visto','estudando','dominado')), updated_at timestamptz DEFAULT now(), PRIMARY KEY (user_id, topic_id));
CREATE INDEX ON attempt (user_id, at); CREATE INDEX ON question_topic (topic_id);
