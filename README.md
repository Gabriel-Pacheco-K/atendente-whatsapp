# atendente-whatsapp

![CI](https://github.com/Gabriel-Pacheco-K/atendente-whatsapp/actions/workflows/ci.yml/badge.svg)
![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)

Um atendente de WhatsApp para pequenos negócios que agenda, lista e cancela horários conversando em português.
Fiz pensando numa barbearia aqui da região: o dono passa o dia respondendo "tem horário amanhã?" e "quanto é a barba?".

Ele grava os horários no [agendamento-api](https://github.com/Gabriel-Pacheco-K/agendamento-api), outro projeto meu.

![Demo: o chat à esquerda e a agenda do agendamento-api à direita, mudando a cada "sim"](docs/demo.gif)

*Tudo rodando de verdade (atendente, agendamento-api e PostgreSQL). A digitação foi automatizada para gravar o GIF.*

## O que ele faz

- Entende frases do jeito que o pessoal escreve: "qro marca amanha de tarde c/ o joao", "15hrs", "sexta".
- Pergunta só o que falta (serviço, profissional, dia, horário, nome) e mostra um resumo antes de marcar.
- Se o horário está ocupado, sugere os mais próximos.
- Lista e cancela os agendamentos do cliente.
- Responde preço, endereço e horário de funcionamento, até no meio de um agendamento.
- Funciona no chat web (uma página parecida com o WhatsApp) e no WhatsApp de verdade, pela Cloud API da Meta.

Não precisa de IA paga: tem um motor de regras próprio para o português. Se quiser, dá para ligar o Claude para as
frases mais difíceis (é opcional e só é chamado quando as regras ficam em dúvida).

## O que eu quis resolver de verdade

**Não marcar duas vezes.** Se o cliente manda "sim" várias vezes seguidas, só sai um agendamento. Cada conversa fica
travada no banco (`SELECT ... FOR UPDATE`) enquanto uma mensagem é processada, e tem um teste com 8 "sim" ao mesmo
tempo provando isso.

**Mensagem repetida não faz nada.** O WhatsApp às vezes reenvia a mesma mensagem. Eu guardo o id de cada mensagem já
processada e ignoro a cópia.

**Webhook seguro.** Toda mensagem que chega da Meta tem a assinatura HMAC conferida antes de qualquer coisa. Sem
assinatura certa, é recusada.

**Nunca dizer "agendado" sem ter agendado.** Se a API de agendamento demora e não sei se marcou, eu não tento de
novo (poderia duplicar): confiro a agenda antes de responder.

**Código organizado em camadas.** Domínio em Java puro, sem Spring, e um teste com ArchUnit que quebra o build se
alguém misturar as camadas.

## Como rodar

Precisa de Docker.

```bash
docker compose up --build
```

Abra http://localhost:8080 e converse. O compose sobe o PostgreSQL, o agendamento-api, os dados da barbearia de
exemplo e o atendente.

Para ligar no WhatsApp com o número de teste grátis da Meta, o passo a passo está em
[docs/detalhes-tecnicos.md](docs/detalhes-tecnicos.md#whatsapp-com-o-número-de-teste-da-meta).

## Testes

```bash
mvn verify
```

São 978 testes: unitários, conversas inteiras simuladas, integração com PostgreSQL real (Testcontainers) e a API de
agendamento simulada com WireMock. No CI ainda roda um teste ponta a ponta com o agendamento-api de verdade.

## Tecnologias

Java 21, Spring Boot 3.3, PostgreSQL 16, Flyway, Testcontainers, WireMock, ArchUnit, Docker e GitHub Actions.

## O que aprendi

- Concorrência de verdade: entender por que duas requisições ao mesmo tempo quebram tudo sem trava.
- Que integrar com API externa é tratar erro: timeout, resposta estranha, serviço fora do ar.
- Que processar texto em português é bem mais difícil do que parece (acento, abreviação, erro de digitação).

## Próximos passos

- Lembrete automático um dia antes do horário.
- Remarcar direto, sem precisar cancelar e marcar de novo.
- Um painel simples para o dono ver a agenda do dia.

Quer ver todas as decisões técnicas em detalhe? Estão em [docs/detalhes-tecnicos.md](docs/detalhes-tecnicos.md).

---

Feito por [Gabriel Pacheco Kiesel](https://gabriel-pacheco-k.github.io).
