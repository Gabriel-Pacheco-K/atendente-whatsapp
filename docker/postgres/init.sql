-- Roda só na criação do volume (para recomeçar do zero: docker compose down -v).
-- Cada serviço tem o próprio banco e o próprio usuário; nenhum lê o banco do outro.
CREATE USER agendamento WITH PASSWORD 'agendamento';
CREATE DATABASE agendamento OWNER agendamento;
CREATE USER atendente WITH PASSWORD 'atendente';
CREATE DATABASE atendente OWNER atendente;
REVOKE CONNECT ON DATABASE agendamento FROM PUBLIC;
REVOKE CONNECT ON DATABASE atendente FROM PUBLIC;
