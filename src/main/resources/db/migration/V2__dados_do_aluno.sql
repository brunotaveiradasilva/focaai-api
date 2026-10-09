-- Estado de estudo de cada aluno. Perfil, progresso, foco e reforço são lidos e gravados inteiros
-- pelo app e pelo plano, então ficam em jsonb numa linha só, travada a cada gravação.
CREATE TABLE aluno (
    usuario_id    uuid PRIMARY KEY REFERENCES usuario ON DELETE CASCADE,
    perfil        jsonb NOT NULL,
    progresso     jsonb NOT NULL DEFAULT '{}',  -- assunto -> aulas feitas
    foco          jsonb NOT NULL DEFAULT '{}',  -- matéria -> assunto escolhido como foco
    reforco       jsonb NOT NULL DEFAULT '{}',  -- matéria -> peso extra no plano (0 a 3)
    criado_em     timestamptz NOT NULL DEFAULT now(),
    atualizado_em timestamptz NOT NULL DEFAULT now()
);

-- O que o aluno registra depois de estudar.
CREATE TABLE checkin (
    id         uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    feito_em   timestamptz NOT NULL,
    tarefa_id  text,
    materia    text,
    topico_id  text,
    minutos    int NOT NULL CHECK (minutos >= 0),
    sensacao   smallint NOT NULL CHECK (sensacao BETWEEN 1 AND 3),  -- 1 fácil, 2 ok, 3 difícil
    questoes   int NOT NULL DEFAULT 0 CHECK (questoes >= 0),
    acertos    int NOT NULL DEFAULT 0 CHECK (acertos >= 0)
);
CREATE INDEX ON checkin (usuario_id, feito_em);

-- Mudanças que o plano fez a partir dos check-ins, mostradas na Evolução (guarda as 20 últimas).
CREATE TABLE ajuste (
    id         bigserial PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    feito_em   timestamptz NOT NULL,
    materia    text,
    texto      text NOT NULL
);
CREATE INDEX ON ajuste (usuario_id, feito_em DESC);
