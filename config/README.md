# Configuração sem rebuild

Copie `src/main/resources/business.yml` para esta pasta e edite: o atendente lê `config/business.yml` por cima do
arquivo interno. As listas (horários, feriados, serviços, perguntas frequentes) são substituídas inteiras, e as
datas vão entre aspas. Depois, `docker compose restart atendente`.
