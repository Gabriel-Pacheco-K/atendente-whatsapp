"use strict";

// Painel do dono: agenda do dia de cada profissional, com concluído, não veio e cancelar.
// Tudo que vem da API entra na página com textContent, nunca como HTML.

const STATUS = {
  SCHEDULED: "Marcado",
  CONFIRMED: "Confirmado",
  COMPLETED: "Concluído",
  NO_SHOW: "Não veio",
  CANCELLED: "Cancelado",
  UNKNOWN: "—",
};

const dinheiro = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const formatoDia = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "2-digit" });

const $ = (id) => document.getElementById(id);
let diaAtual = hojeLocal();

function hojeLocal() {
  const agora = new Date();
  agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset());
  return agora.toISOString().slice(0, 10);
}

function somarDias(iso, dias) {
  const data = new Date(iso + "T12:00:00");
  data.setDate(data.getDate() + dias);
  return data.toISOString().slice(0, 10);
}

function el(tag, classe, texto) {
  const node = document.createElement(tag);
  if (classe) node.className = classe;
  if (texto !== undefined) node.textContent = texto;
  return node;
}

function mostrarLogin() {
  $("painel").hidden = true;
  $("login").hidden = false;
  $("senha").focus();
}

function avisar(texto) {
  $("aviso").textContent = texto;
  $("aviso").hidden = !texto;
}

async function carregar() {
  avisar("");
  let resposta;
  try {
    resposta = await fetch("/api/painel/agenda?date=" + encodeURIComponent(diaAtual),
      { credentials: "same-origin" });
  } catch {
    avisar("Sem conexão com o servidor. Tente de novo.");
    return;
  }
  if (resposta.status === 401) {
    mostrarLogin();
    return;
  }
  if (!resposta.ok) {
    avisar("A agenda não respondeu agora. Toque em Atualizar daqui a pouco.");
    return;
  }
  $("login").hidden = true;
  $("painel").hidden = false;
  desenhar(await resposta.json());
}

function desenhar(agenda) {
  const dia = formatoDia.format(new Date(agenda.date + "T12:00:00"));
  $("titulo-dia").textContent = agenda.date === hojeLocal() ? "Hoje · " + dia : dia;
  $("hoje").hidden = agenda.date === hojeLocal();
  $("total-agendamentos").textContent = agenda.appointments;
  $("total-concluidos").textContent = agenda.completed;
  $("total-previsto").textContent = dinheiro.format(agenda.expected);
  $("total-feito").textContent = dinheiro.format(agenda.done);

  const lista = $("profissionais");
  lista.replaceChildren();
  for (const profissional of agenda.professionals) {
    const bloco = el("section", "profissional");
    bloco.append(el("h2", null, profissional.name));
    if (profissional.appointments.length === 0) {
      bloco.append(el("p", "vazio", "Nenhum horário marcado."));
    } else {
      const ul = el("ul", "lista");
      for (const item of profissional.appointments) ul.append(cartao(item));
      bloco.append(ul);
    }
    lista.append(bloco);
  }
}

function cartao(item) {
  const li = el("li", "horario status-" + item.status);

  const linha1 = el("div", "linha");
  linha1.append(el("span", "hora", item.start + " – " + item.end), el("span", "selo", STATUS[item.status] || "—"));

  const linha2 = el("div", "linha");
  linha2.append(el("span", null, item.service), el("span", "preco", item.price != null ? dinheiro.format(item.price) : ""));

  const cliente = el("div", "cliente");
  cliente.append(document.createTextNode(item.customer + " · "));
  const telefone = el("a", null, item.phone);
  telefone.href = "tel:" + String(item.phone).replace(/[^\d+]/g, "");
  cliente.append(telefone);

  li.append(linha1, linha2, cliente);

  if (item.status === "SCHEDULED" || item.status === "CONFIRMED") {
    const acoes = el("div", "acoes");
    acoes.append(
      botao("Concluído", null, () => agir(item.id, "concluir")),
      botao("Não veio", "secundario", () => agir(item.id, "nao-veio")),
      botao("Cancelar", "perigo", () => {
        if (confirm("Cancelar o horário de " + item.customer + " às " + item.start + "?")) agir(item.id, "cancelar");
      }),
    );
    li.append(acoes);
  }
  return li;
}

function botao(texto, classe, aoClicar) {
  const b = el("button", classe, texto);
  b.type = "button";
  b.addEventListener("click", aoClicar);
  return b;
}

async function agir(id, acao) {
  document.querySelectorAll(".acoes button").forEach((b) => { b.disabled = true; });
  const resposta = await fetch("/api/painel/agendamentos/" + encodeURIComponent(id) + "/" + acao,
    { method: "POST", credentials: "same-origin" }).catch(() => null);
  if (!resposta) {
    avisar("Sem conexão com o servidor. Tente de novo.");
    document.querySelectorAll(".acoes button").forEach((b) => { b.disabled = false; });
    return;
  }
  if (resposta.status === 401) {
    mostrarLogin();
    return;
  }
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => ({}));
    avisar(problema.detail || "Não deu para alterar esse horário agora.");
    document.querySelectorAll(".acoes button").forEach((b) => { b.disabled = false; });
    return;
  }
  carregar();
}

$("login-form").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  $("login-erro").textContent = "";
  const resposta = await fetch("/api/painel/login", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password: $("senha").value }),
  }).catch(() => null);
  if (resposta && resposta.ok) {
    $("senha").value = "";
    carregar();
  } else if (resposta && resposta.status === 429) {
    $("login-erro").textContent = "Muitas tentativas. Espere um minuto.";
  } else if (resposta && resposta.status === 401) {
    $("login-erro").textContent = "Senha incorreta.";
  } else {
    $("login-erro").textContent = "Não deu para entrar agora. Tente de novo.";
  }
});

$("dia-anterior").addEventListener("click", () => { diaAtual = somarDias(diaAtual, -1); carregar(); });
$("dia-seguinte").addEventListener("click", () => { diaAtual = somarDias(diaAtual, 1); carregar(); });
$("hoje").addEventListener("click", () => { diaAtual = hojeLocal(); carregar(); });
$("atualizar").addEventListener("click", carregar);
$("sair").addEventListener("click", async () => {
  await fetch("/api/painel/logout", { method: "POST", credentials: "same-origin" }).catch(() => null);
  mostrarLogin();
});

carregar();
