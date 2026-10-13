-- Plano de cada dia, congelado quando o aluno abre o app naquele dia: as tarefas atrasadas são
-- conferidas contra o que foi planejado, não contra o plano de hoje. Guarda os 21 dias mais recentes.
CREATE TABLE plano_dia (
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    dia        date NOT NULL,
    tarefas    jsonb NOT NULL,
    PRIMARY KEY (usuario_id, dia)
);

-- Tarefas atrasadas que o aluno dispensou ("deixar pra lá"). Guarda as 200 mais recentes.
CREATE TABLE tarefa_dispensada (
    usuario_id uuid NOT NULL REFERENCES usuario ON DELETE CASCADE,
    tarefa_id  text NOT NULL,
    em         timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (usuario_id, tarefa_id)
);
