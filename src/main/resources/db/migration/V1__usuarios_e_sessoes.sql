-- Quem entra com a conta Google. O id do Google (sub) é a identidade; o e-mail pode mudar.
CREATE TABLE usuario (
    id              uuid PRIMARY KEY,
    google_sub      text NOT NULL UNIQUE,
    email           text NOT NULL,
    nome            text,
    foto            text,
    criado_em       timestamptz NOT NULL DEFAULT now(),
    ultimo_login_em timestamptz NOT NULL DEFAULT now()
);

-- Uma linha por aparelho logado. Guarda só o hash do refresh token: vazar o banco não vaza sessões.
CREATE TABLE sessao (
    id          uuid PRIMARY KEY,
    usuario_id  uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    token_hash  text NOT NULL UNIQUE,
    criada_em   timestamptz NOT NULL DEFAULT now(),
    expira_em   timestamptz NOT NULL,
    revogada_em timestamptz
);
CREATE INDEX ON sessao (usuario_id);
