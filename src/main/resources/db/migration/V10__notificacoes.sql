-- Atividade do aluno, como a do Instagram: pedidos e aceites de amizade, comentários nos seus
-- posts, posts novos dos amigos e o lembrete de fazer simulado. ator_id: quem fez (nulo no
-- lembrete). Some junto com a conta de quem recebe, de quem fez ou com o post.
CREATE TABLE notificacao (
    id         bigserial PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    tipo       text NOT NULL CHECK (tipo IN ('friend_request', 'friend_accepted', 'comment', 'post', 'simulado_reminder')),
    ator_id    uuid REFERENCES usuario ON DELETE CASCADE,
    post_id    bigint REFERENCES post ON DELETE CASCADE,
    trecho     text,
    criada_em  timestamptz NOT NULL DEFAULT now(),
    lida_em    timestamptz
);
CREATE INDEX ON notificacao (usuario_id, id DESC);
CREATE INDEX notificacao_nao_lida ON notificacao (usuario_id) WHERE lida_em IS NULL;
