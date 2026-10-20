-- Fotos enviadas pelo app (de perfil e de posts). Ficam no próprio banco porque são pequenas: o
-- app reduz e comprime antes de enviar. O id é aleatório e serve de endereço público da imagem.
CREATE TABLE imagem (
    id         uuid PRIMARY KEY,
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    tipo       text NOT NULL,
    dados      bytea NOT NULL,
    criada_em  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ON imagem (usuario_id);

-- Uma foto opcional por post; com foto, o texto pode ficar vazio.
ALTER TABLE post ADD COLUMN imagem_id uuid REFERENCES imagem ON DELETE SET NULL;

-- Nome ou foto mudados no app: o login com Google para de sobrescrevê-los.
ALTER TABLE usuario ADD COLUMN perfil_editado boolean NOT NULL DEFAULT false;
