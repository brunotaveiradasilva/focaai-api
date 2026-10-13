-- As provas guardadas antes da correção de língua estrangeira podem ter inglês e espanhol misturados
-- (2011) ou faltar (2009 e 2011 em inglês). Limpar faz a API baixar de novo, já com as regras novas.
-- Simulados já feitos guardam as próprias questões e não mudam.
DELETE FROM prova_enem;
