-- Pedidos de conexão com parceiros de estudo (por enquanto, pessoas de exemplo; ver Pessoas.java).
CREATE TABLE conexao (
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    pessoa_id  text NOT NULL,
    status     text NOT NULL CHECK (status IN ('pending', 'connected')),
    em         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, pessoa_id)
);

-- Grupos de estudo em que o aluno entrou.
CREATE TABLE grupo_membro (
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    grupo_id   text NOT NULL,
    em         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, grupo_id)
);
