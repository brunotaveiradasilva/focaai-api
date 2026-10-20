-- Rede social entre alunos: amizades, posts e comentários. Posts só aparecem para o autor e os amigos dele.

-- Uma frase sobre o aluno, mostrada no perfil.
ALTER TABLE usuario ADD COLUMN bio text;

-- Pedido de amizade de "de" para "para"; vira amizade quando "para" aceita (aceita_em preenchido).
-- Cada par tem uma linha só, não importa quem pediu.
CREATE TABLE amizade (
    de        uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    para      uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    pedida_em timestamptz NOT NULL DEFAULT now(),
    aceita_em timestamptz,
    PRIMARY KEY (de, para),
    CHECK (de <> para)
);
CREATE UNIQUE INDEX amizade_par_unico ON amizade (least(de, para), greatest(de, para));
CREATE INDEX ON amizade (para);

CREATE TABLE post (
    id        bigserial PRIMARY KEY,
    autor_id  uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    texto     text NOT NULL,
    criado_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON post (autor_id, id DESC);

CREATE TABLE comentario (
    id        bigserial PRIMARY KEY,
    post_id   bigint NOT NULL REFERENCES post ON DELETE CASCADE,
    autor_id  uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    texto     text NOT NULL,
    criado_em timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON comentario (post_id, id);
