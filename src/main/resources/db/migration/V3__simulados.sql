-- Provas do ENEM baixadas da api.enem.dev (domínio público), já convertidas. Compartilhadas por
-- todos os alunos: cada prova é baixada uma vez só.
CREATE TABLE prova_enem (
    ano        int  NOT NULL,
    lingua     text NOT NULL CHECK (lingua IN ('ingles', 'espanhol')),
    questoes   jsonb NOT NULL,
    baixada_em timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (ano, lingua)
);

-- Simulados do aluno. Sem finalizado_em = o simulado em andamento (no máximo um por aluno).
CREATE TABLE simulado (
    id            uuid PRIMARY KEY,
    usuario_id    uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    criado_em     timestamptz NOT NULL,
    finalizado_em timestamptz,
    areas         jsonb NOT NULL,
    questoes      jsonb NOT NULL,
    respostas     jsonb NOT NULL DEFAULT '{}',
    segundos      int NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX simulado_em_andamento ON simulado (usuario_id) WHERE finalizado_em IS NULL;
CREATE INDEX ON simulado (usuario_id, finalizado_em DESC);
