-- Entrar também com e-mail e senha. Toda conta tem o e-mail confirmado (pelo Google ou por código),
-- então o e-mail identifica a conta: quem entra com Google e com senha no mesmo e-mail cai na mesma.
ALTER TABLE usuario ALTER COLUMN google_sub DROP NOT NULL;
ALTER TABLE usuario ADD COLUMN senha_hash text;
-- Senha errada várias vezes seguidas bloqueia a conta por um tempo (contra quem tenta adivinhar).
ALTER TABLE usuario ADD COLUMN falhas_de_senha integer NOT NULL DEFAULT 0;
ALTER TABLE usuario ADD COLUMN bloqueada_ate timestamptz;
CREATE UNIQUE INDEX usuario_email_unico ON usuario (lower(email));

-- Código de 6 dígitos mandado por e-mail, para criar a conta ou trocar a senha. Um por e-mail e
-- finalidade; só o hash fica guardado. No cadastro, guarda também o nome e a senha (já em hash) até
-- o código ser confirmado: a conta só passa a existir depois disso.
CREATE TABLE codigo_de_email (
    id          uuid PRIMARY KEY,
    email       text NOT NULL,
    finalidade  text NOT NULL,
    codigo_hash text NOT NULL,
    nome        text,
    senha_hash  text,
    tentativas  integer NOT NULL DEFAULT 0,
    criado_em   timestamptz NOT NULL DEFAULT now(),
    expira_em   timestamptz NOT NULL,
    UNIQUE (email, finalidade)
);
